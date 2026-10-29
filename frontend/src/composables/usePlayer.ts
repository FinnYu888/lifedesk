import { ref } from "vue";
import { ElMessage } from "element-plus";
import { trackSrc, updateMusicDuration, type MusicTrack } from "@/api/music";

/**
 * 全局音乐播放器（模块级单例）。
 *
 * - audio 元素在模块作用域创建，不属于任何组件 → 菜单切换/页面跳转播放不中断；
 * - MainLayout 右上角迷你展示与「我的音乐」页共享同一份状态；
 * - 播放队列：点列表播放时传入整页列表，播完自动切下一曲（循环），支持上一首/下一首；
 * - 进度：timeupdate 同步 progress，页面进度条拖拽时经 seek() 跳转；
 * - 音量：0~1，持久化 localStorage。
 */

/** 当前播放曲目 */
const current = ref<MusicTrack | null>(null);
/** 是否播放中 */
const playing = ref(false);
/** 进度（秒）与总时长 */
const progress = ref(0);
const duration = ref(0);
/** 音量 0~1（持久化） */
const volume = ref(parseFloat(localStorage.getItem("lifedesk-volume") || "") || 0.8);

// 模块级唯一 audio 实例：生命周期与页面（SPA）一致，切菜单不会销毁
const audio: HTMLAudioElement | null = typeof Audio !== "undefined" ? new Audio() : null;

/** 播放队列（当前列表），ended 时顺序循环 */
let queue: MusicTrack[] = [];

if (audio) {
  audio.preload = "metadata";
  audio.volume = Math.min(1, Math.max(0, volume.value));
  audio.addEventListener("play", () => (playing.value = true));
  audio.addEventListener("pause", () => (playing.value = false));
  audio.addEventListener("timeupdate", () => (progress.value = audio.currentTime || 0));
  audio.addEventListener("durationchange", () => {
    duration.value = isFinite(audio.duration) ? audio.duration : 0;
    // 回填曲目时长（仅上传文件后端不知道时长；外链也可回填，失败静默）
    if (current.value && duration.value > 0 && current.value.sourceType === "upload") {
      updateMusicDuration(current.value.id, Math.round(duration.value)).catch(() => {});
    }
  });
  audio.addEventListener("ended", () => {
    // 队列循环：播完自动下一曲
    next();
  });
  audio.addEventListener("error", () => {
    playing.value = false;
    // 播放失败明确提示（此前静默吞掉，用户点播放没声音也没原因）
    const title = current.value?.title || "该曲目";
    ElMessage.warning(`「${title}」无法播放：不是有效的音频直链或源文件已失效`);
  });
}

/** 播放指定曲目（list 为所在列表，用于自动连播） */
function playTrack(track: MusicTrack, list?: MusicTrack[]) {
  if (!audio || !track) return;
  if (list && list.length) {
    queue = list;
  } else if (!queue.some((t) => t.id === track.id)) {
    queue = [track, ...queue];
  }
  if (current.value?.id === track.id && audio.src) {
    // 同一曲：切播放/暂停
    toggle();
    return;
  }
  current.value = track;
  audio.src = trackSrc(track);
  progress.value = 0;
  audio.play().catch(() => (playing.value = false));
}

/** 播放/暂停切换 */
function toggle() {
  if (!audio || !current.value) return;
  if (audio.paused) {
    audio.play().catch(() => {});
  } else {
    audio.pause();
  }
}

/** 下一曲（队列循环） */
function next() {
  if (!audio || !queue.length) {
    playing.value = false;
    return;
  }
  const idx = queue.findIndex((t) => t.id === current.value?.id);
  const nextTrack = queue[(idx + 1) % queue.length];
  if (nextTrack && nextTrack.id !== current.value?.id) {
    playTrack(nextTrack, queue);
  } else {
    // 队列只有一曲：从头重播
    audio.currentTime = 0;
    audio.play().catch(() => {});
  }
}

/** 上一曲：播放超 3 秒先回到开头，否则切上一首（主流播放器习惯） */
function prev() {
  if (!audio || !current.value) return;
  if (audio.currentTime > 3) {
    audio.currentTime = 0;
    return;
  }
  const idx = queue.findIndex((t) => t.id === current.value?.id);
  const prevTrack = queue[(idx - 1 + queue.length) % queue.length];
  if (prevTrack && prevTrack.id !== current.value?.id) {
    playTrack(prevTrack, queue);
  } else {
    audio.currentTime = 0;
  }
}

/** 跳转到指定秒数（进度条拖拽释放后调用） */
function seek(sec: number) {
  if (!audio || !current.value) return;
  const d = isFinite(audio.duration) ? audio.duration : duration.value;
  if (d > 0) {
    audio.currentTime = Math.min(Math.max(0, sec), d - 0.2);
    progress.value = audio.currentTime;
  }
}

/** 设置音量 0~1 并持久化 */
function setVolume(v: number) {
  volume.value = Math.min(1, Math.max(0, v));
  if (audio) audio.volume = volume.value;
  localStorage.setItem("lifedesk-volume", String(volume.value));
}

/** 停止并清空当前曲目 */
function stop() {
  if (!audio) return;
  audio.pause();
  audio.removeAttribute("src");
  current.value = null;
  playing.value = false;
  progress.value = 0;
  duration.value = 0;
  queue = [];
}

export function usePlayer() {
  return {
    current,
    playing,
    progress,
    duration,
    volume,
    playTrack,
    toggle,
    stop,
    next,
    prev,
    seek,
    setVolume,
    hasQueue: queue.length > 0
  };
}

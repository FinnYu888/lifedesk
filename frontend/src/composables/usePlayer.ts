import { ref } from "vue";
import { trackSrc, updateMusicDuration, type MusicTrack } from "@/api/music";

/**
 * 全局音乐播放器（模块级单例）。
 *
 * - audio 元素在模块作用域创建，不属于任何组件 → 菜单切换/页面跳转播放不中断；
 * - MainLayout 右上角迷你展示与「我的音乐」页共享同一份状态；
 * - 播放队列：点列表播放时传入整页列表，播完自动切下一曲（循环）。
 */

/** 当前播放曲目 */
const current = ref<MusicTrack | null>(null);
/** 是否播放中 */
const playing = ref(false);
/** 进度（秒）与总时长 */
const progress = ref(0);
const duration = ref(0);

// 模块级唯一 audio 实例：生命周期与页面（SPA）一致，切菜单不会销毁
const audio: HTMLAudioElement | null = typeof Audio !== "undefined" ? new Audio() : null;

/** 播放队列（当前列表），ended 时顺序循环 */
let queue: MusicTrack[] = [];

if (audio) {
  audio.preload = "metadata";
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
    const idx = queue.findIndex((t) => t.id === current.value?.id);
    if (queue.length > 1) {
      const next = queue[(idx + 1) % queue.length];
      playTrack(next, queue);
    } else {
      playing.value = false;
      progress.value = 0;
    }
  });
  audio.addEventListener("error", () => {
    playing.value = false;
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
  return { current, playing, progress, duration, playTrack, toggle, stop };
}

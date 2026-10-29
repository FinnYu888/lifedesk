<template>
  <div class="music-page">
    <!-- 页头：标题 + 搜索 + 新增 -->
    <div class="mp-head">
      <div class="mp-title-wrap">
        <div class="mp-title">我的音乐</div>
        <div class="mp-sub">支持外链或上传源文件 · 播放不随菜单切换停止</div>
      </div>
      <div class="mp-head-actions">
        <el-input
          v-model="keyword"
          class="mp-search"
          placeholder="搜索歌名 / 歌手"
          clearable
          :prefix-icon="Search"
        />
        <el-button type="primary" :icon="Plus" @click="openAddDialog">新增音乐</el-button>
      </div>
    </div>

    <div class="mp-main">
      <!-- 左：正在播放（黑胶唱片面板） -->
      <aside class="ld-card mp-now">
        <div class="np-disc" :class="{ spinning: playing }">
          <div class="np-label">
            <el-icon :size="26"><Headset /></el-icon>
          </div>
          <div class="np-hole" />
        </div>
        <template v-if="current">
          <div class="np-title" :title="current.title">{{ current.title }}</div>
          <div class="np-artist">{{ current.artist || "未知歌手" }}</div>
          <div class="np-tags">
            <span class="np-tag">{{ sourceText(current) }}</span>
            <span v-if="duration > 0" class="np-tag">{{ fmtTime(duration) }}</span>
            <span v-if="playing" class="np-tag live">正在播放</span>
          </div>
          <!-- 歌词区：LRC 同步高亮 / 纯文本滚动 -->
          <div v-if="lyricsShow" ref="lyricsBox" class="np-lyrics" v-loading="lyricsLoading">
            <template v-if="lyricsLines.length">
              <div
                v-for="(l, i) in lyricsLines"
                :key="i"
                :data-li="i"
                class="ly-line"
                :class="{ active: i === activeLine }"
              >{{ l.text || "♪" }}</div>
              <div class="ly-pad" />
            </template>
            <div v-else-if="lyricsMissing" class="ly-empty">
              未在歌词库找到该曲歌词，可点击下方「编辑歌词」手动粘贴 LRC 或纯文本
            </div>
          </div>
          <div v-if="current" class="np-ly-actions">
            <button class="np-ly-btn" @click="toggleLyrics">
              <el-icon :size="13"><Tickets /></el-icon>
              {{ lyricsShow ? "收起歌词" : (lyricsText ? "查看歌词" : "加载歌词") }}
            </button>
            <button v-if="lyricsShow" class="np-ly-btn ghost" @click="openLyricsEdit">编辑歌词</button>
          </div>
        </template>
        <template v-else>
          <div class="np-title placeholder">未在播放</div>
          <div class="np-artist">从右侧曲库选择一首歌开始</div>
        </template>
      </aside>

      <!-- 右：曲目列表 -->
      <section class="ld-card mp-list-card">
        <div class="mp-list-head">
          <span>曲库 · {{ filtered.length }} 首</span>
          <span v-if="keyword" class="mp-list-filtered">（已过滤）</span>
        </div>
        <div v-loading="loading" class="mp-list">
          <div
            v-for="(t, i) in filtered"
            :key="t.id"
            class="mp-row"
            :class="{ active: current?.id === t.id }"
            @dblclick="play(t)"
          >
            <div class="mp-idx">
              <span class="idx-num" v-if="current?.id !== t.id">{{ i + 1 }}</span>
              <span v-else class="eq" :class="{ paused: !playing }"><i /><i /><i /><i /></span>
            </div>
            <el-icon
              class="mp-play-btn"
              :size="20"
              :title="current?.id === t.id && playing ? '暂停' : '播放'"
              @click="play(t)"
            >
              <VideoPause v-if="current?.id === t.id && playing" />
              <VideoPlay v-else />
            </el-icon>
            <div class="mp-info">
              <div class="mp-name" :title="t.title">{{ t.title }}</div>
              <div class="mp-meta">
                <span>{{ t.artist || "未知歌手" }}</span>
                <span class="mp-dot">·</span>
                <span>{{ sourceText(t) }}</span>
                <span v-if="t.fileSize" class="mp-dot">·</span>
                <span v-if="t.fileSize">{{ fmtSize(t.fileSize) }}</span>
              </div>
            </div>
            <div class="mp-time">{{ t.durationSec ? fmtTime(t.durationSec) : "—" }}</div>
            <div class="mp-actions">
              <el-button link :icon="Switch" :disabled="replacing" @click="pickReplace(t)">替换音频</el-button>
              <el-button link type="danger" :icon="Delete" @click="onDelete(t)">删除</el-button>
            </div>
          </div>
          <div v-if="!loading && !filtered.length" class="mp-empty">
            <el-icon :size="28"><Headset /></el-icon>
            <span>{{ keyword ? "没有匹配的歌曲" : "曲库为空，点击右上角「新增音乐」添加第一首歌" }}</span>
          </div>
        </div>
      </section>
    </div>

    <!-- 替换音频用隐藏文件选择器 -->
    <input
      ref="replaceInput"
      type="file"
      accept=".mp3,.wav,.ogg,.m4a,.aac,.flac,.wma,.ape,.opus,.webm"
      style="display: none"
      @change="onReplaceFile"
    />

    <!-- 底部播放条：切歌 / 播放暂停 / 可拖拽进度 / 音量 -->
    <div class="ld-card mp-player" :class="{ disabled: !current }">
      <div class="pl-controls">
        <el-icon class="pl-btn" :size="18" title="上一首" @click="player.prev()"><DArrowLeft /></el-icon>
        <button class="pl-toggle" :title="playing ? '暂停' : '播放'" @click="player.toggle()">
          <el-icon :size="20">
            <VideoPause v-if="playing" />
            <VideoPlay v-else />
          </el-icon>
        </button>
        <el-icon class="pl-btn" :size="18" title="下一首" @click="player.next()"><DArrowRight /></el-icon>
      </div>

      <div class="pl-track" :title="current?.title">
        <span class="pl-name">{{ current?.title || "—" }}</span>
        <span class="pl-artist">{{ current?.artist || "" }}</span>
      </div>

      <span class="pl-time">{{ fmtTime(dispTime) }}</span>
      <div
        ref="barRef"
        class="pl-bar"
        :class="{ dragging }"
        @pointerdown="onSeekDown"
        @pointermove="onSeekMove"
        @pointerup="onSeekUp"
        @pointercancel="onSeekUp"
      >
        <div class="pl-bar-fill" :style="{ width: pct + '%' }" />
        <div class="pl-bar-thumb" :style="{ left: pct + '%' }" />
      </div>
      <span class="pl-time total">{{ fmtTime(duration) }}</span>

      <el-icon
        class="pl-btn"
        :class="{ 'pl-ly-on': lyricsShow }"
        :size="16"
        title="歌词"
        @click="toggleLyrics"
      ><Tickets /></el-icon>

      <div class="pl-volume">
        <svg class="pl-vol-icon" viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
          <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3a4.5 4.5 0 0 0-2.5-4.03v8.05A4.5 4.5 0 0 0 16.5 12z" />
        </svg>
        <el-slider
          class="pl-vol-slider"
          :model-value="Math.round(volume * 100)"
          :show-tooltip="false"
          size="small"
          @input="(v: number) => player.setVolume(v / 100)"
        />
      </div>
    </div>

    <!-- 新增音乐弹窗：链接 / 上传源文件 -->
    <el-dialog
      v-model="addDialog.visible"
      title="新增音乐"
      width="min(520px, 94%)"
      :close-on-click-modal="false"
      class="ld-dialog"
      modal-class="ld-overlay"
    >
      <el-radio-group v-model="addDialog.mode" class="add-mode">
        <el-radio-button label="link">音乐链接</el-radio-button>
        <el-radio-button label="upload">上传源文件</el-radio-button>
      </el-radio-group>

      <!-- 链接方式 -->
      <el-form v-if="addDialog.mode === 'link'" label-width="76px" label-position="left" class="add-form">
        <el-form-item label="歌曲名" required>
          <el-input v-model="addDialog.title" placeholder="如 晴天" maxlength="128" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-input v-model="addDialog.artist" placeholder="如 周杰伦" maxlength="64" />
        </el-form-item>
        <el-form-item label="音乐链接" required>
          <el-input
            v-model="addDialog.url"
            placeholder="mp3 等音频直链，如 https://example.com/song.mp3"
          />
        </el-form-item>
      </el-form>

      <!-- 上传方式（支持批量，文件名与曲库同名的自动替换该曲音频） -->
      <el-form v-else label-width="76px" label-position="left" class="add-form">
        <el-form-item label="音乐文件" required>
          <el-upload
            :auto-upload="false"
            multiple
            accept=".mp3,.wav,.ogg,.m4a,.aac,.flac,.wma,.ape,.opus,.webm"
            :on-change="onFilePick"
            :on-remove="onFileRemove"
            drag
            class="upload-box"
          >
            <el-icon :size="28"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em>（可多选）</div>
            <template #tip>
              <div class="el-upload__tip">
                支持 mp3 / wav / ogg / m4a / flac 等，单个不超过 50MB；
                文件名与曲库歌名相同的将自动替换该曲音频
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="歌曲名">
          <el-input v-model="addDialog.title" placeholder="留空则取文件名（单文件时生效）" maxlength="128" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-input v-model="addDialog.artist" placeholder="选填（单文件时生效）" maxlength="64" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="addDialog.submitting" @click="submitAdd">
          {{ addDialog.mode === 'link' ? '添加链接' : '上传并添加' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑歌词弹窗 -->
    <el-dialog
      v-model="lyEdit.visible"
      :title="current ? `编辑歌词 - ${current.title}` : '编辑歌词'"
      width="min(560px, 94%)"
      :close-on-click-modal="false"
      class="ld-dialog"
      modal-class="ld-overlay"
    >
      <el-input
        v-model="lyEdit.text"
        type="textarea"
        :rows="14"
        placeholder="粘贴 LRC 同步歌词（格式 [00:12.00]歌词行）或纯文本歌词"
      />
      <template #footer>
        <el-button @click="lyEdit.visible = false">取消</el-button>
        <el-button type="primary" :loading="lyEdit.saving" @click="submitLyrics">保存歌词</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Plus, Delete, VideoPlay, VideoPause, UploadFilled, Headset, Search,
  DArrowLeft, DArrowRight, Switch, Tickets
} from "@element-plus/icons-vue";
import {
  fetchMusicList,
  addMusicLink,
  deleteMusic,
  uploadMusicFile,
  replaceMusicFile,
  fetchMusicLyrics,
  saveMusicLyrics,
  type MusicTrack
} from "@/api/music";
import { usePlayer } from "@/composables/usePlayer";

defineOptions({ name: "MusicView" });

const player = usePlayer();
const { current, playing, progress, duration, volume } = player;

const tracks = ref<MusicTrack[]>([]);
const loading = ref(false);
const keyword = ref("");

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase();
  if (!k) return tracks.value;
  return tracks.value.filter(
    (t) =>
      (t.title || "").toLowerCase().includes(k) ||
      (t.artist || "").toLowerCase().includes(k)
  );
});

async function load() {
  loading.value = true;
  try {
    const r: any = await fetchMusicList();
    tracks.value = r?.data || [];
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false;
  }
}

/** 播放（传入整页列表实现自动连播） */
function play(t: MusicTrack) {
  player.playTrack(t, tracks.value);
}

// ===================== 进度条拖拽 =====================
const barRef = ref<HTMLElement | null>(null);
const dragging = ref(false);
const dragPct = ref(0);

/** 展示百分比：拖拽中用拖拽位置，否则用实际进度 */
const pct = computed(() => {
  if (dragging.value) return dragPct.value;
  if (!duration.value) return 0;
  return Math.min(100, (progress.value / duration.value) * 100);
});
/** 展示时间：拖拽中预览目标位置，否则当前进度 */
const dispTime = computed(() => {
  if (dragging.value) return (dragPct.value / 100) * duration.value;
  return progress.value;
});

function posPct(e: PointerEvent) {
  const el = barRef.value;
  if (!el || !duration.value) return 0;
  const rect = el.getBoundingClientRect();
  return Math.min(100, Math.max(0, ((e.clientX - rect.left) / rect.width) * 100));
}
function onSeekDown(e: PointerEvent) {
  if (!duration.value || !current.value) return;
  // 捕获指针：移出进度条也能持续拖拽
  (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
  dragging.value = true;
  dragPct.value = posPct(e);
}
function onSeekMove(e: PointerEvent) {
  if (!dragging.value) return;
  dragPct.value = posPct(e);
}
function onSeekUp(e: PointerEvent) {
  if (!dragging.value) return;
  const p = posPct(e);
  dragPct.value = p;
  player.seek((p / 100) * duration.value);
  dragging.value = false;
}

// ===================== 歌词 =====================
const lyricsShow = ref(false);
const lyricsText = ref("");
const lyricsSynced = ref(false);
const lyricsLoading = ref(false);
const lyricsMissing = ref(false);
const lyricsBox = ref<HTMLElement | null>(null);

/** LRC 解析：[mm:ss.xx]行 → {t, text}；无时间轴行 t=null（纯文本歌词） */
function parseLrc(text: string): { t: number | null; text: string }[] {
  const out: { t: number | null; text: string }[] = [];
  const re = /\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\]/g;
  for (const raw of (text || "").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    let m: RegExpExecArray | null;
    let last = 0;
    const times: number[] = [];
    re.lastIndex = 0;
    while ((m = re.exec(line))) {
      const frac = m[3] ? +(("0".repeat(3 - m[3].length) + m[3]).slice(0, 3)) / 1000 : 0;
      times.push(+m[1] * 60 + +m[2] + frac);
      last = re.lastIndex;
    }
    const content = line.slice(last).trim();
    if (times.length) {
      for (const t of times) out.push({ t, text: content });
    } else {
      out.push({ t: null, text: line });
    }
  }
  return out.sort((a, b) => (a.t ?? 0) - (b.t ?? 0));
}

const lyricsLines = computed(() => parseLrc(lyricsText.value));

/** 当前应高亮的歌词行（按播放进度） */
const activeLine = computed(() => {
  if (!lyricsSynced.value) return -1;
  let idx = -1;
  lyricsLines.value.forEach((l, i) => {
    if (l.t != null && l.t <= progress.value + 0.2) idx = i;
  });
  return idx;
});

/** 换曲自动拉取歌词 */
watch(
  () => current.value?.id,
  () => {
    lyricsText.value = "";
    lyricsSynced.value = false;
    lyricsMissing.value = false;
    if (current.value && lyricsShow.value) loadLyrics();
  }
);

async function loadLyrics() {
  const t = current.value;
  if (!t) return;
  if (t.lyrics) {
    lyricsText.value = t.lyrics;
    lyricsSynced.value = t.lyrics.includes("[");
    lyricsMissing.value = false;
    return;
  }
  lyricsLoading.value = true;
  try {
    const r: any = await fetchMusicLyrics(t.id);
    lyricsText.value = r?.data?.lyrics || "";
    lyricsSynced.value = !!r?.data?.synced;
    lyricsMissing.value = !lyricsText.value;
    // 回填列表对象，避免重复请求
    const item = tracks.value.find((x) => x.id === t.id);
    if (item) item.lyrics = lyricsText.value;
  } catch {
    lyricsMissing.value = true;
  } finally {
    lyricsLoading.value = false;
  }
}

function toggleLyrics() {
  lyricsShow.value = !lyricsShow.value;
  if (lyricsShow.value && current.value && !lyricsText.value && !lyricsLoading.value && !lyricsMissing.value) {
    loadLyrics();
  }
}

/** 活动歌词行自动滚动到面板中部 */
watch(activeLine, async (i) => {
  if (i < 0 || !lyricsBox.value) return;
  await nextTick();
  const el = lyricsBox.value.querySelector<HTMLElement>(`[data-li="${i}"]`);
  if (el) {
    lyricsBox.value.scrollTo({
      top: el.offsetTop - lyricsBox.value.clientHeight / 2 + el.clientHeight / 2,
      behavior: "smooth"
    });
  }
});

// 歌词编辑
const lyEdit = reactive<{ visible: boolean; text: string; saving: boolean }>({
  visible: false,
  text: "",
  saving: false
});
function openLyricsEdit() {
  lyEdit.text = lyricsText.value;
  lyEdit.visible = true;
}
async function submitLyrics() {
  if (!current.value) return;
  lyEdit.saving = true;
  try {
    await saveMusicLyrics(current.value.id, lyEdit.text);
    lyricsText.value = lyEdit.text;
    lyricsSynced.value = lyEdit.text.includes("[");
    lyricsMissing.value = false;
    const item = tracks.value.find((x) => x.id === current.value?.id);
    if (item) item.lyrics = lyricsText.value;
    lyEdit.visible = false;
    ElMessage.success("歌词已保存");
  } catch (e: any) {
    ElMessage.error(e?.message || "保存失败");
  } finally {
    lyEdit.saving = false;
  }
}

// ===================== 新增音乐 =====================
const addDialog = reactive<{
  visible: boolean;
  mode: "link" | "upload";
  title: string;
  artist: string;
  url: string;
  files: File[];
  submitting: boolean;
}>({
  visible: false,
  mode: "link",
  title: "",
  artist: "",
  url: "",
  files: [],
  submitting: false
});

function openAddDialog() {
  addDialog.mode = "link";
  addDialog.title = "";
  addDialog.artist = "";
  addDialog.url = "";
  addDialog.files = [];
  addDialog.visible = true;
}

/** 累积选择/拖入的文件（可多选） */
function onFilePick(uploadFile: any) {
  if (uploadFile?.raw) addDialog.files.push(uploadFile.raw as File);
}

/** 从待上传列表移除 */
function onFileRemove(uploadFile: any) {
  addDialog.files = addDialog.files.filter((f) => f !== uploadFile?.raw);
}

/** 文件名去扩展名 */
function stripExt(name: string) {
  return name.replace(/\.[^.]+$/, "").trim();
}

async function submitAdd() {
  addDialog.submitting = true;
  try {
    if (addDialog.mode === "link") {
      if (!addDialog.title.trim()) {
        ElMessage.warning("请填写歌曲名");
        return;
      }
      if (!addDialog.url.trim()) {
        ElMessage.warning("请填写音乐链接");
        return;
      }
      await addMusicLink({
        title: addDialog.title,
        artist: addDialog.artist || undefined,
        url: addDialog.url
      });
      ElMessage.success("链接已添加");
    } else {
      if (!addDialog.files.length) {
        ElMessage.warning("请选择音乐文件");
        return;
      }
      // 批量上传：文件名与曲库歌名相同 → 替换该曲音频（保留歌名/歌手）；否则新增
      let added = 0;
      let replaced = 0;
      let failed = 0;
      for (const f of addDialog.files) {
        try {
          const match = tracks.value.find((t) => (t.title || "").trim() === stripExt(f.name));
          if (match) {
            await replaceMusicFile(match.id, f);
            // 替换的是正在播放的曲目 → 停止，避免继续播旧缓存
            if (current.value?.id === match.id) player.stop();
            replaced++;
          } else {
            const customTitle = addDialog.files.length === 1 ? addDialog.title || undefined : undefined;
            const customArtist = addDialog.files.length === 1 ? addDialog.artist || undefined : undefined;
            await uploadMusicFile(f, customTitle, customArtist);
            added++;
          }
        } catch {
          failed++;
        }
      }
      ElMessage.success(
        `上传完成：新增 ${added} 首 · 替换 ${replaced} 首${failed ? ` · 失败 ${failed} 首` : ""}`
      );
    }
    addDialog.visible = false;
    await load();
  } catch (e: any) {
    ElMessage.error(e?.message || "添加失败");
  } finally {
    addDialog.submitting = false;
  }
}

// ===================== 替换音频（单曲） =====================
const replaceInput = ref<HTMLInputElement | null>(null);
const replaceTarget = ref<MusicTrack | null>(null);
const replacing = ref(false);

/** 打开文件选择器，为指定曲目更换音频文件 */
function pickReplace(t: MusicTrack) {
  replaceTarget.value = t;
  replaceInput.value?.click();
}

async function onReplaceFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  const target = replaceTarget.value;
  if (!file || !target) return;
  replacing.value = true;
  try {
    await replaceMusicFile(target.id, file);
    if (current.value?.id === target.id) player.stop();
    ElMessage.success(`「${target.title}」音频已替换为完整歌曲文件`);
    await load();
  } catch (err: any) {
    ElMessage.error(err?.message || "替换失败");
  } finally {
    replacing.value = false;
    replaceTarget.value = null;
  }
}

// ===================== 删除 =====================
async function onDelete(t: MusicTrack) {
  await ElMessageBox.confirm(`确定删除「${t.title}」吗？`, "删除确认", {
    type: "warning",
    confirmButtonText: "删除",
    cancelButtonText: "取消"
  });
  await deleteMusic(t.id);
  ElMessage.success("已删除");
  // 删除的是当前播放曲目 → 停止播放
  if (current.value?.id === t.id) {
    player.stop();
  }
  await load();
}

// ===================== 展示辅助 =====================
function sourceText(t: MusicTrack) {
  return t.sourceType === "upload" ? "上传文件" : "外链";
}
function fmtSize(bytes: number) {
  if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + " MB";
  return Math.round(bytes / 1024) + " KB";
}
function fmtTime(sec: number) {
  if (!sec || !isFinite(sec) || sec < 0) return "0:00";
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

onMounted(load);
</script>

<style scoped lang="scss">
.music-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 100%;
}
.mp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.mp-title {
  font-size: 20px;
  font-weight: 700;
  color: $text-primary;
}
.mp-sub {
  font-size: 12px;
  color: $text-tertiary;
  margin-top: 3px;
}
.mp-head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.mp-search {
  width: 200px;
}

/* ============ 主区：左正在播放 + 右曲库 ============ */
.mp-main {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  min-width: 0;
}
@media (max-width: 900px) {
  .mp-main {
    grid-template-columns: minmax(0, 1fr);
  }
  .mp-now {
    flex-direction: row !important;
    align-items: center;
    .np-disc {
      width: 84px !important;
      height: 84px !important;
      margin: 0 !important;
    }
    .np-title {
      margin-top: 0 !important;
      text-align: left;
    }
  }
}

/* 正在播放面板：黑胶唱片 */
.mp-now {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 26px 20px;
  text-align: center;
  min-width: 0;
}
.np-disc {
  position: relative;
  width: 168px;
  height: 168px;
  border-radius: 50%;
  background:
    repeating-radial-gradient(circle at 50% 50%,
      rgba(255, 255, 255, 0.05) 0 1px,
      rgba(0, 0, 0, 0.28) 1px 4px),
    radial-gradient(circle at 50% 50%, #2a2440 0%, #17122a 70%);
  border: 1px solid $border-glass;
  box-shadow: $shadow-card, $glow-accent;
  display: flex;
  align-items: center;
  justify-content: center;
}
.np-disc.spinning {
  animation: disc-spin 9s linear infinite;
}
@keyframes disc-spin {
  to { transform: rotate(360deg); }
}
.np-label {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  background: linear-gradient(135deg, $accent, $accent-light);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.06);
}
.np-hole {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: $bg-page;
  border: 2px solid rgba(255, 255, 255, 0.25);
}
.np-title {
  margin-top: 18px;
  font-size: 17px;
  font-weight: 700;
  color: $text-primary;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  &.placeholder {
    color: $text-tertiary;
    font-weight: 600;
  }
}
.np-artist {
  margin-top: 5px;
  font-size: 13px;
  color: $text-secondary;
}
.np-tags {
  display: flex;
  gap: 6px;
  margin-top: 12px;
  flex-wrap: wrap;
  justify-content: center;
}
.np-tag {
  font-size: 11px;
  color: $text-secondary;
  padding: 2px 9px;
  border: 1px solid $border-subtle;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
  &.live {
    color: $accent-light;
    border-color: $border-accent;
  }
}

/* 歌词区 */
.np-ly-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.np-ly-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  font-size: 12px;
  font-family: inherit;
  color: $text-secondary;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid $border-subtle;
  border-radius: 999px;
  cursor: pointer;
  &:hover {
    color: $accent-light;
    border-color: $border-accent;
  }
  &.ghost {
    color: $text-tertiary;
  }
}
.np-lyrics {
  width: 100%;
  max-height: 220px;
  margin-top: 14px;
  overflow-y: auto;
  text-align: center;
  scroll-behavior: smooth;
  mask-image: linear-gradient(transparent, #000 12%, #000 88%, transparent);
  -webkit-mask-image: linear-gradient(transparent, #000 12%, #000 88%, transparent);
  padding: 20px 6px;
}
.ly-line {
  font-size: 13px;
  line-height: 2;
  color: $text-tertiary;
  padding: 2px 0;
  transition: color 0.25s, transform 0.25s;
  &.active {
    color: $text-primary;
    font-weight: 700;
    transform: scale(1.06);
  }
}
.ly-pad {
  height: 40%;
}
.ly-empty {
  font-size: 12px;
  color: $text-tertiary;
  padding: 20px 0;
}

/* 曲库列表 */
.mp-list-card {
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.mp-list-head {
  font-size: 12px;
  color: $text-tertiary;
  padding: 4px 8px 10px;
}
.mp-list-filtered {
  color: $accent-light;
}
.mp-list {
  flex: 1;
  min-height: 120px;
  overflow-y: auto;
}
.mp-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-bottom: 1px dashed $border-subtle;
  border-radius: $radius-sm;
  &:last-child {
    border-bottom: none;
  }
  &:hover {
    background: rgba(255, 255, 255, 0.03);
    .mp-idx .idx-num {
      opacity: 0;
    }
    .mp-play-btn {
      opacity: 1;
    }
  }
  &.active {
    background: rgba(124, 92, 255, 0.08);
  }
}
.mp-idx {
  width: 20px;
  text-align: center;
  flex-shrink: 0;
  .idx-num {
    font-size: 12px;
    color: $text-tertiary;
    font-variant-numeric: tabular-nums;
  }
}
.mp-play-btn {
  flex-shrink: 0;
  color: $accent-light;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
  &.el-icon {
    margin-left: -32px;
  }
}
.mp-row.active .mp-play-btn,
.mp-row:hover .mp-play-btn {
  opacity: 1;
}
.mp-row.active .mp-idx .idx-num {
  opacity: 0;
}
.mp-info {
  flex: 1;
  min-width: 0;
}
.mp-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mp-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: $text-tertiary;
  margin-top: 3px;
  overflow: hidden;
}
.mp-dot {
  color: $text-tertiary;
}
.mp-time {
  flex-shrink: 0;
  font-size: 12px;
  color: $text-secondary;
  width: 44px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.mp-actions {
  flex-shrink: 0;
}
.mp-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 36px 0;
  color: $text-tertiary;
  font-size: 13px;
}

/* ============ 底部播放条 ============ */
.mp-player {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  backdrop-filter: blur(14px);
  &.disabled {
    opacity: 0.55;
  }
}
.pl-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.pl-btn {
  color: $text-secondary;
  cursor: pointer;
  &:hover {
    color: $accent-light;
  }
  &.pl-ly-on {
    color: $accent-light;
  }
}
.pl-toggle {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, $accent, $accent-light);
  box-shadow: $glow-accent;
  &:hover {
    filter: brightness(1.08);
  }
}
.pl-track {
  display: flex;
  flex-direction: column;
  min-width: 0;
  width: 150px;
  flex-shrink: 0;
}
.pl-name {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-artist {
  font-size: 11px;
  color: $text-tertiary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-height: 14px;
}
.pl-time {
  flex-shrink: 0;
  font-size: 12px;
  color: $text-secondary;
  width: 42px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.pl-bar {
  position: relative;
  flex: 1;
  min-width: 60px;
  height: 14px;
  cursor: pointer;
  touch-action: none;
  user-select: none;
  /* 加高点击热区 */
  &::before {
    content: "";
    position: absolute;
    left: 0;
    right: 0;
    top: 5px;
    height: 4px;
    border-radius: 2px;
    background: rgba(255, 255, 255, 0.12);
  }
  &.dragging .pl-bar-thumb,
  &:hover .pl-bar-thumb {
    transform: translate(-50%, -50%) scale(1.25);
  }
}
.pl-bar-fill {
  position: absolute;
  left: 0;
  top: 5px;
  height: 4px;
  border-radius: 2px;
  background: linear-gradient(90deg, $accent, $accent-light);
  pointer-events: none;
}
.pl-bar-thumb {
  position: absolute;
  top: 7px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 8px rgba(0, 0, 0, 0.4), 0 0 10px $accent;
  transform: translate(-50%, -50%) scale(0.9);
  transition: transform 0.12s;
  pointer-events: none;
}
.pl-volume {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  width: 130px;
}
.pl-vol-icon {
  color: $text-secondary;
  flex-shrink: 0;
}
.pl-vol-slider {
  flex: 1;
  min-width: 0;
}

@media (max-width: 720px) {
  .pl-track {
    display: none;
  }
  .pl-volume {
    width: 90px;
  }
  .mp-search {
    width: 150px;
  }
}

/* 播放中的动态均衡器（4 根柱跳动，暂停时静止变暗） */
.eq {
  display: inline-flex;
  align-items: flex-end;
  gap: 2px;
  height: 12px;
  flex-shrink: 0;
  i {
    display: inline-block;
    width: 3px;
    background: $accent-light;
    border-radius: 1px;
    animation: eq-bounce 0.9s ease-in-out infinite;
    &:nth-child(1) { height: 6px; animation-delay: 0s; }
    &:nth-child(2) { height: 11px; animation-delay: 0.2s; }
    &:nth-child(3) { height: 8px; animation-delay: 0.4s; }
    &:nth-child(4) { height: 12px; animation-delay: 0.6s; }
  }
  &.paused i {
    animation-play-state: paused;
    opacity: 0.45;
  }
}
@keyframes eq-bounce {
  0%, 100% { transform: scaleY(0.4); }
  50% { transform: scaleY(1); }
}
</style>

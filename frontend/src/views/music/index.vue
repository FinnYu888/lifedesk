<template>
  <div class="music-page">
    <!-- 页头：标题 + 新增 -->
    <div class="mp-head">
      <div class="mp-title-wrap">
        <div class="mp-title">我的音乐</div>
        <div class="mp-sub">支持外链或上传源文件 · 播放不随菜单切换停止</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAddDialog">新增音乐</el-button>
    </div>

    <!-- 曲目列表 -->
    <div class="ld-card mp-list-card">
      <div v-loading="loading" class="mp-list">
        <div
          v-for="t in tracks"
          :key="t.id"
          class="mp-row"
          :class="{ active: current?.id === t.id }"
          @dblclick="play(t)"
        >
          <el-icon class="mp-play-btn" :size="20" @click="play(t)">
            <VideoPlay v-if="current?.id !== t.id || !playing" />
            <VideoPause v-else />
          </el-icon>
          <div class="mp-info">
            <div class="mp-name">
              {{ t.title }}
              <!-- 播放中的动态均衡器 -->
              <span v-if="current?.id === t.id" class="eq" :class="{ paused: !playing }">
                <i /><i /><i /><i />
              </span>
            </div>
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
            <el-button link type="danger" :icon="Delete" @click="onDelete(t)">删除</el-button>
          </div>
        </div>
        <div v-if="!loading && !tracks.length" class="mp-empty">
          <el-icon :size="28"><Headset /></el-icon>
          <span>曲库为空，点击右上角「新增音乐」添加第一首歌</span>
        </div>
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

      <!-- 上传方式 -->
      <el-form v-else label-width="76px" label-position="left" class="add-form">
        <el-form-item label="音乐文件" required>
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept=".mp3,.wav,.ogg,.m4a,.aac,.flac,.wma,.ape,.opus,.webm"
            :on-change="onFilePick"
            :on-remove="() => (addDialog.file = null)"
            drag
            class="upload-box"
          >
            <el-icon :size="28"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 mp3 / wav / ogg / m4a / flac 等，单个不超过 50MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="歌曲名">
          <el-input v-model="addDialog.title" placeholder="留空则取文件名" maxlength="128" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-input v-model="addDialog.artist" placeholder="选填" maxlength="64" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="addDialog.submitting" @click="submitAdd">
          {{ addDialog.mode === 'link' ? '添加链接' : '上传并添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Delete, VideoPlay, VideoPause, UploadFilled, Headset } from "@element-plus/icons-vue";
import {
  fetchMusicList,
  addMusicLink,
  deleteMusic,
  uploadMusicFile,
  type MusicTrack
} from "@/api/music";
import { usePlayer } from "@/composables/usePlayer";

defineOptions({ name: "MusicView" });

const { current, playing, playTrack } = usePlayer();

const tracks = ref<MusicTrack[]>([]);
const loading = ref(false);

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
  playTrack(t, tracks.value);
}

// ===================== 新增音乐 =====================
const addDialog = reactive<{
  visible: boolean;
  mode: "link" | "upload";
  title: string;
  artist: string;
  url: string;
  file: File | null;
  submitting: boolean;
}>({
  visible: false,
  mode: "link",
  title: "",
  artist: "",
  url: "",
  file: null,
  submitting: false
});

function openAddDialog() {
  addDialog.mode = "link";
  addDialog.title = "";
  addDialog.artist = "";
  addDialog.url = "";
  addDialog.file = null;
  addDialog.visible = true;
}

function onFilePick(uploadFile: any) {
  addDialog.file = uploadFile?.raw || null;
  // 歌曲名默认取文件名（去扩展名）
  if (uploadFile?.name && !addDialog.title) {
    addDialog.title = uploadFile.name.replace(/\.[^.]+$/, "");
  }
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
      if (!addDialog.file) {
        ElMessage.warning("请选择音乐文件");
        return;
      }
      await uploadMusicFile(addDialog.file, addDialog.title || undefined, addDialog.artist || undefined);
      ElMessage.success("文件已上传");
    }
    addDialog.visible = false;
    await load();
  } catch (e: any) {
    ElMessage.error(e?.message || "添加失败");
  } finally {
    addDialog.submitting = false;
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
    const { stop } = usePlayer();
    stop();
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
  const m = Math.floor(sec / 60);
  const s = Math.round(sec % 60);
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

.mp-list-card {
  padding: 8px 14px;
}
.mp-list {
  min-height: 120px;
}
.mp-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 8px;
  border-bottom: 1px dashed $border-subtle;
  border-radius: $radius-sm;
  &:last-child {
    border-bottom: none;
  }
  &:hover {
    background: rgba(255, 255, 255, 0.03);
  }
  &.active {
    background: rgba(124, 92, 255, 0.08);
  }
}
.mp-play-btn {
  flex-shrink: 0;
  color: $accent-light;
  cursor: pointer;
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

.add-mode {
  margin-bottom: 16px;
}
.add-form {
  margin-top: 4px;
}
.upload-box {
  width: 100%;
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

@media (max-width: 640px) {
  .mp-time {
    display: none;
  }
}
</style>

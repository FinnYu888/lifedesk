<template>
  <div class="apps-page">
    <!-- 页头：标题 + 添加按钮 -->
    <div class="apps-header">
      <div>
        <div class="apps-title">常用App</div>
        <div class="apps-subtitle">像桌面快捷方式一样展示本机应用，点击图标即可启动（在本机打开，不嵌入系统内）</div>
      </div>
      <button class="ld-btn ld-btn-primary" @click="openAddDialog">
        <el-icon><Plus /></el-icon> 添加应用
      </button>
    </div>

    <!-- 快捷方式网格：桌面图标风格（图标在上、名称在下），单击启动 -->
    <div v-if="apps.length" class="shortcuts">
      <div
        v-for="a in apps"
        :key="a.id"
        class="shortcut"
        :title="a.appPath"
        @click="onLaunch(a)"
      >
        <el-icon class="shortcut-del" title="移除" @click.stop="onDelete(a)"><CircleClose /></el-icon>
        <div class="shortcut-icon">
          <img
            v-if="!iconFailed[a.id]"
            :src="iconUrl(a.id)"
            alt=""
            draggable="false"
            @error="iconFailed[a.id] = true"
          />
          <el-icon v-else :size="40"><Cpu /></el-icon>
        </div>
        <div class="shortcut-name">{{ a.name }}</div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="apps-empty">
      <el-icon :size="44"><Cpu /></el-icon>
      <div class="empty-title">还没有添加应用</div>
      <div class="empty-hint">点击右上角「添加应用」，选择本机程序（exe / lnk / bat）创建快捷方式</div>
    </div>

    <!-- 添加应用弹窗 -->
    <div v-if="addVisible" class="modal-overlay" @click.self="addVisible = false">
      <div class="modal ld-card">
        <div class="modal-title">添加应用</div>
        <div class="modal-row">
          <label>应用名称</label>
          <el-input v-model="form.name" placeholder="如 计算器 / IDEA / 微信" maxlength="64" />
        </div>
        <div class="modal-row">
          <label>启动路径</label>
          <div class="path-row">
            <el-input v-model="form.appPath" placeholder="C:\Windows\System32\calc.exe" />
            <button class="ld-btn ld-btn-ghost nowrap" @click="openBrowse('')">
              <el-icon><FolderOpened /></el-icon> 浏览
            </button>
          </div>
        </div>
        <div class="modal-row">
          <label>启动参数</label>
          <el-input v-model="form.args" placeholder="可选，如 --minimized" />
        </div>
        <div class="modal-hint">保存后展示为桌面快捷方式风格的图标（与系统一致），点击即在本机真实启动。支持 exe / lnk / bat / url / jar。</div>
        <div class="modal-footer">
          <button class="ld-btn ld-btn-ghost" @click="addVisible = false">取消</button>
          <button class="ld-btn ld-btn-primary" :disabled="submitting" @click="onSubmit">
            {{ submitting ? "保存中…" : "保存" }}
          </button>
        </div>
      </div>
    </div>

    <!-- 目录浏览对话框：选择启动路径 -->
    <div v-if="browseVisible" class="modal-overlay" @click.self="browseVisible = false">
      <div class="modal ld-card browse-modal">
        <div class="modal-title">选择应用 · {{ browsePath || "此电脑" }}</div>
        <div class="browse-list">
          <div
            v-if="browseParent !== null && browseParent !== undefined"
            class="browse-item"
            @click="openBrowse(browseParent || '')"
          >
            <span class="bi-icon">↩</span>
            <span class="bi-name">上级目录</span>
          </div>
          <div
            v-for="it in browseItems"
            :key="it.path"
            class="browse-item"
            @click="it.dir ? openBrowse(it.path) : pickPath(it.path)"
          >
            <span class="bi-icon">{{ it.dir ? "📁" : "⚙" }}</span>
            <span class="bi-name">{{ it.name }}</span>
          </div>
          <div v-if="!browseItems.length" class="browse-item browse-empty">空目录</div>
        </div>
        <div class="modal-footer">
          <button class="ld-btn ld-btn-ghost" @click="browseVisible = false">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, CircleClose, Cpu, FolderOpened } from "@element-plus/icons-vue";
import {
  fetchNativeApps,
  createNativeApp,
  deleteNativeApp,
  launchNativeApp,
  nativeAppIconUrl,
  browseLocalDir,
  type NativeAppItem,
  type BrowseItem
} from "@/api/nativeApp";

defineOptions({ name: "Apps" });

const apps = ref<NativeAppItem[]>([]);
/** 图标加载失败标记（回退占位图标），按应用 id 索引 */
const iconFailed = ref<Record<number, boolean>>({});

function iconUrl(id: number) {
  return nativeAppIconUrl(id);
}

async function loadApps() {
  try {
    const r = await fetchNativeApps();
    apps.value = (r as any).data || [];
  } catch {
    /* 拦截器已提示 */
  }
}

/** 点击快捷方式：本机真实启动 */
async function onLaunch(a: NativeAppItem) {
  try {
    await launchNativeApp(a.id);
    ElMessage.success(`已启动「${a.name}」`);
  } catch {
    /* 拦截器已提示 */
  }
}

async function onDelete(a: NativeAppItem) {
  try {
    await ElMessageBox.confirm(`确定移除「${a.name}」？`, "移除应用", {
      confirmButtonText: "移除",
      cancelButtonText: "取消",
      type: "warning"
    });
  } catch {
    return;
  }
  try {
    await deleteNativeApp(a.id);
    ElMessage.success("已移除");
  } catch {
    /* 拦截器已提示 */
  }
  loadApps();
}

// ===== 添加应用弹窗 =====
const addVisible = ref(false);
const submitting = ref(false);
const form = ref<Partial<NativeAppItem>>({});

function openAddDialog() {
  form.value = {};
  addVisible.value = true;
}

async function onSubmit() {
  if (!form.value.name?.trim() || !form.value.appPath?.trim()) {
    ElMessage.warning("请填写应用名称和启动路径");
    return;
  }
  submitting.value = true;
  try {
    await createNativeApp(form.value);
    addVisible.value = false;
    ElMessage.success("应用已添加");
    loadApps();
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false;
  }
}

// ===== 目录浏览（选择启动路径） =====
const browseVisible = ref(false);
const browsePath = ref("");
const browseParent = ref<string | null>(null);
const browseItems = ref<BrowseItem[]>([]);

async function openBrowse(dir: string) {
  try {
    const r = await browseLocalDir(dir);
    const d = (r as any).data || {};
    browsePath.value = d.path || "";
    browseParent.value = d.parent;
    browseItems.value = d.items || [];
    browseVisible.value = true;
  } catch {
    /* 拦截器已提示 */
  }
}

function pickPath(path: string) {
  form.value.appPath = path;
  if (!form.value.name?.trim()) {
    // 默认名取文件名去扩展名
    const base = path.split("\\").pop() || path;
    form.value.name = base.replace(/\.(exe|lnk|bat|cmd|url|jar)$/i, "");
  }
  browseVisible.value = false;
}

onMounted(loadApps);
</script>

<style scoped lang="scss">
.apps-page {
  padding: 22px 26px;
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.apps-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.apps-title {
  font-size: 20px;
  font-weight: 700;
  color: $text-primary;
}
.apps-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: $text-tertiary;
}

/* 桌面快捷方式风格网格：图标在上、名称在下，透明底，悬停高亮（同桌面选中效果） */
.shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(108px, 1fr));
  gap: 10px;
}
.shortcut {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px 12px;
  border-radius: 12px;
  border: 1px solid transparent;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s, border-color 0.15s, transform 0.15s;
  &:hover {
    background: rgba(167, 139, 250, 0.1);
    border-color: rgba(167, 139, 250, 0.25);
    transform: translateY(-2px);
    .shortcut-del {
      opacity: 1;
    }
  }
  &:active {
    transform: translateY(0);
  }
}
.shortcut-del {
  position: absolute;
  top: 6px;
  right: 6px;
  font-size: 16px;
  color: $text-tertiary;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  &:hover {
    color: #f87171;
  }
}
.shortcut-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  img {
    width: 48px;
    height: 48px;
    object-fit: contain;
    -webkit-user-drag: none;
  }
  // 占位图标颜色随主题
  .el-icon {
    color: $accent-light;
  }
}
.shortcut-name {
  max-width: 100%;
  font-size: 13px;
  color: $text-primary;
  text-align: center;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
}

/* 空状态 */
.apps-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: $text-tertiary;
  .empty-title {
    font-size: 16px;
    font-weight: 600;
    color: $text-secondary;
  }
  .empty-hint {
    font-size: 12px;
  }
}

/* 弹窗（与系统内其他弹窗同风格） */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(10, 8, 20, 0.45);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.modal {
  width: min(460px, 92%);
  padding: 20px 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.modal-title {
  font-size: 16px;
  font-weight: 700;
  color: $text-primary;
}
.modal-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  label {
    font-size: 12px;
    color: $text-secondary;
  }
}
.modal-hint {
  font-size: 12px;
  color: $text-tertiary;
  line-height: 1.6;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.path-row {
  display: flex;
  gap: 8px;
  .el-input {
    flex: 1;
  }
  .nowrap {
    white-space: nowrap;
  }
}

/* 目录浏览 */
.browse-modal {
  width: min(620px, 92%);
}
.browse-list {
  max-height: 46vh;
  overflow-y: auto;
  border: 1px solid $border-subtle;
  border-radius: $radius-sm;
  padding: 4px;
}
.browse-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: $text-secondary;
  transition: background 0.12s;
  &:hover {
    background: rgba(167, 139, 250, 0.1);
    color: $text-primary;
  }
  .bi-icon {
    width: 20px;
    text-align: center;
    flex-shrink: 0;
  }
  .bi-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &.browse-empty {
    cursor: default;
    justify-content: center;
    color: $text-tertiary;
  }
}

/* 奶白主题下弹窗遮罩与悬停底色换暖调 */
html[data-theme="cream"] .modal-overlay {
  background: rgba(90, 64, 22, 0.22);
}
html[data-theme="cream"] .shortcut:hover {
  background: rgba(192, 138, 62, 0.12);
  border-color: rgba(192, 138, 62, 0.3);
}
html[data-theme="cream"] .browse-item:hover {
  background: rgba(192, 138, 62, 0.1);
}
</style>

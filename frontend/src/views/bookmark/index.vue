<template>
  <div class="bookmark-page">
    <!-- 顶部搜索 + 操作 -->
    <div class="ld-card top-bar">
      <el-input
        v-model="keyword"
        class="search"
        placeholder="搜索名称、URL 或笔记内容…"
        clearable
        @input="onSearch"
      >
        <template #suffix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <div class="top-actions">
        <button class="ld-btn ld-btn-ghost" @click="openCreate('link')">
          <el-icon><Plus /></el-icon> 新建收藏
        </button>
        <button class="ld-btn ld-btn-ghost" @click="openCreate('note')">
          <el-icon><Plus /></el-icon> 新建笔记
        </button>
      </div>
    </div>

    <!-- 双栏：左=收藏，右=笔记 -->
    <div class="dual-pane">
      <!-- 收藏卡片 -->
      <div class="ld-card pane">
        <div class="pane-head">
          <span class="pane-title">收藏</span>
          <span class="pane-count">{{ links.length }} 项</span>
        </div>
        <div class="pane-body">
          <div v-if="links.length === 0" class="empty">暂无收藏</div>
          <div
            v-for="item in links"
            :key="item.id"
            class="fold-card link-card"
            @click="openLink(item)"
          >
            <div class="fold-head">
              <el-icon class="fold-icon"><Link /></el-icon>
              <span class="fold-name">{{ item.name }}</span>
              <span v-if="item.description" class="fold-tag">{{ item.description }}</span>
              <div class="fold-actions" @click.stop>
                <el-icon class="op-icon" @click="openEdit(item)"><Edit /></el-icon>
                <el-icon class="op-icon danger" @click="onDelete(item)"><Delete /></el-icon>
              </div>
            </div>
            <div class="fold-body">
              <div class="link-url">{{ item.value }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 笔记卡片（上下折叠） -->
      <div class="ld-card pane">
        <div class="pane-head">
          <span class="pane-title">笔记</span>
          <span class="pane-count">{{ notes.length }} 项</span>
        </div>
        <div class="pane-body">
          <div v-if="notes.length === 0" class="empty">暂无笔记</div>
          <div
            v-for="item in notes"
            :key="item.id"
            class="fold-card note-card"
            :class="{ expanded: expandedId === item.id }"
          >
            <div class="fold-head" @click="toggleExpand(item.id)">
              <el-icon class="fold-icon"><Document /></el-icon>
              <span class="fold-name">{{ item.name }}</span>
              <span v-if="item.description" class="fold-tag">{{ item.description }}</span>
              <span class="fold-time">{{ formatTime(item.updateTime || item.createTime) }}</span>
              <el-icon class="chevron">
                <component :is="expandedId === item.id ? 'ArrowUp' : 'ArrowDown'" />
              </el-icon>
              <div class="fold-actions" @click.stop>
                <el-icon class="op-icon" @click="openEdit(item)"><Edit /></el-icon>
                <el-icon class="op-icon danger" @click="onDelete(item)"><Delete /></el-icon>
              </div>
            </div>
            <transition name="expand">
              <div v-show="expandedId === item.id" class="fold-body">
                <pre class="note-content">{{ item.value }}</pre>
              </div>
            </transition>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <div v-if="dialogVisible" class="modal-overlay" @click.self="dialogVisible = false">
      <div class="modal ld-card">
        <div class="modal-title">{{ form.id ? "编辑" : "新建" }}{{ form.type === "link" ? "收藏" : "笔记" }}</div>
        <div class="modal-row">
          <label>名称</label>
          <el-input v-model="form.name" placeholder="收藏网站名 / 笔记标题" maxlength="128" />
        </div>
        <div class="modal-row">
          <label>{{ form.type === "link" ? "URL" : "内容" }}</label>
          <el-input
            v-model="form.value"
            :type="form.type === 'note' ? 'textarea' : 'text'"
            :rows="form.type === 'note' ? 5 : undefined"
            :placeholder="form.type === 'note' ? '笔记正文（支持多行）' : 'https://...'"
          />
        </div>
        <div class="modal-row">
          <label>备注</label>
          <el-input v-model="form.description" placeholder="分类/标签，可选" maxlength="32" />
        </div>
        <div class="modal-footer">
          <button class="ld-btn ld-btn-ghost" @click="dialogVisible = false">取消</button>
          <button class="ld-btn ld-btn-primary" :disabled="submitting" @click="onSubmit">
            {{ submitting ? "保存中…" : "保存" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Plus, Link, Document, Edit, Delete, ArrowUp, ArrowDown } from "@element-plus/icons-vue";
import {
  fetchFavorites,
  createFavorite,
  updateFavorite,
  deleteFavorite,
  type Favorite
} from "@/api/bookmark";

defineOptions({ name: "BookmarkView" });

const keyword = ref("");
const links = ref<Favorite[]>([]);
const notes = ref<Favorite[]>([]);
const expandedId = ref<number | null>(null);

const dialogVisible = ref(false);
const submitting = ref(false);
const form = ref<Favorite>({ name: "", value: "", type: "link", description: "" });

let searchTimer: any = null;

async function load() {
  try {
    const res = await fetchFavorites({ keyword: keyword.value || undefined });
    const all: Favorite[] = (res as any).data || [];
    links.value = all.filter((x) => x.type === "link");
    notes.value = all.filter((x) => x.type === "note");
  } catch {
    /* 拦截器已提示 */
  }
}

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(load, 250);
}

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id;
}

function openLink(item: Favorite) {
  if (item.value) window.open(item.value, "_blank");
}

function openCreate(type: "link" | "note") {
  form.value = { name: "", value: "", type, description: "" };
  dialogVisible.value = true;
}

function openEdit(item: Favorite) {
  form.value = { ...item };
  dialogVisible.value = true;
}

async function onSubmit() {
  if (!form.value.name.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  if (!form.value.value.trim()) {
    ElMessage.warning("请填写" + (form.value.type === "link" ? " URL" : "内容"));
    return;
  }
  submitting.value = true;
  try {
    if (form.value.id) {
      await updateFavorite(form.value.id, { ...form.value });
      ElMessage.success("已保存");
    } else {
      await createFavorite({ ...form.value });
      ElMessage.success("已创建");
    }
    dialogVisible.value = false;
    await load();
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false;
  }
}

async function onDelete(item: Favorite) {
  try {
    await ElMessageBox.confirm(`确定删除「${item.name}」？`, "提示", { type: "warning" });
    await deleteFavorite(item.id!);
    ElMessage.success("已删除");
    await load();
  } catch {
    /* 取消或拦截器已提示 */
  }
}

function formatTime(t?: string) {
  if (!t) return "";
  return (t as string).replace("T", " ").substring(5, 16);
}

onMounted(load);
</script>

<style scoped lang="scss">
.bookmark-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
}
.search {
  flex: 1;
  max-width: 520px;
}
.top-actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
}

.dual-pane {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-height: calc(100vh - 200px);
}
.pane {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}
.pane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 14px 18px 10px;
  border-bottom: 1px solid $border-glass;
}
.pane-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}
.pane-count {
  font-size: 12px;
  color: $text-tertiary;
}
.pane-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.empty {
  text-align: center;
  color: $text-tertiary;
  padding: 40px 0;
  font-size: 13px;
}

.fold-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid $border-glass;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.18s;
  &:hover {
    border-color: $border-accent;
    background: rgba(167, 139, 250, 0.05);
  }
  &.expanded {
    background: rgba(167, 139, 250, 0.08);
  }
}
.link-card {
  cursor: pointer;
}
.fold-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-size: 13px;
}
.fold-icon {
  color: $accent-light;
  font-size: 14px;
}
.fold-name {
  color: $text-primary;
  font-weight: 500;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fold-tag {
  font-size: 11px;
  color: $text-tertiary;
  padding: 2px 8px;
  background: rgba(167, 139, 250, 0.1);
  border-radius: 8px;
}
.fold-time {
  font-size: 11px;
  color: $text-tertiary;
}
.chevron {
  color: $text-tertiary;
  font-size: 12px;
  cursor: pointer;
}
.fold-actions {
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.18s;
  pointer-events: none;
}
.fold-card:hover .fold-actions {
  opacity: 1;
  pointer-events: auto;
}
.op-icon {
  cursor: pointer;
  color: $text-tertiary;
  font-size: 14px;
  &:hover {
    color: $accent-light;
  }
  &.danger:hover {
    color: #f87171;
  }
}
.fold-body {
  padding: 0 12px 12px;
  font-size: 12px;
}
.link-url {
  color: $accent-light;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
}
.note-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.6;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.2s;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.modal {
  width: 480px;
  max-width: 100%;
  padding: 22px 24px;
  background: $bg-card;
}
.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 18px;
}
.modal-row {
  margin-bottom: 14px;
  label {
    display: block;
    font-size: 12px;
    color: $text-tertiary;
    margin-bottom: 6px;
  }
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
.ld-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid $border-glass;
  background: rgba(255, 255, 255, 0.04);
  color: $text-secondary;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover:not(:disabled) {
    background: rgba(167, 139, 250, 0.1);
    color: $text-primary;
  }
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}
.ld-btn-primary {
  background: linear-gradient(135deg, $accent, $accent-light);
  color: #fff;
  border: none;
  &:hover:not(:disabled) {
    box-shadow: 0 4px 14px rgba(167, 139, 250, 0.35);
    color: #fff;
  }
}
</style>

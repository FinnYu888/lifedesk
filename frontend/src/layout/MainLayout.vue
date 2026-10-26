<template>
  <div class="layout">
    <!-- 左侧固定侧边栏（液态玻璃） -->
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-logo">L</div>
        <span class="brand-name">LifeDesk</span>
      </div>

      <nav class="nav-group">
        <div class="nav-title">工作台</div>
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="nav-item-active"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>

        <!-- 自定义菜单：用户自行添加的外部网站（iframe 嵌入当前系统） -->
        <div class="nav-title">自定义</div>
        <router-link
          v-for="m in customMenus"
          :key="m.id"
          :to="{ path: '/custom', query: { id: String(m.id), name: m.name, url: m.url } }"
          class="nav-item custom-nav-item"
          :class="{ 'nav-item-active': isCurrentMenu(m) }"
        >
          <el-icon><component :is="iconOf(m.icon)" /></el-icon>
          <span class="custom-nav-label">{{ m.name }}</span>
          <el-icon class="custom-nav-del" @click.prevent.stop="onDeleteMenu(m)"><CircleClose /></el-icon>
        </router-link>

        <!-- 新增菜单按钮：与上面 nav-item 同宽同高，点击弹出对话框填名称+URL -->
        <button class="nav-item nav-add" @click="openAddDialog">
          <el-icon><Plus /></el-icon>
          <span>新增菜单</span>
        </button>
      </nav>

      <!-- 今日健康迷你卡 -->
      <div class="health-mini">
        <div class="mini-title">今日健康</div>
        <div class="mini-row">
          <span class="mini-key">步数</span>
          <span class="mini-val">8,425 / 10k · 84%</span>
        </div>
        <div class="mini-row">
          <span class="mini-key">消耗</span>
          <span class="mini-val text-positive">-320 kcal · 赤字</span>
        </div>
        <div class="mini-row">
          <span class="mini-key">体重</span>
          <span class="mini-val">65.2 kg · -0.8</span>
        </div>
      </div>

      <div class="user-area">
        <div class="avatar">林</div>
        <span class="nickname">林深</span>
      </div>
    </aside>

    <!-- 右侧主区域 -->
    <div class="main">
      <header class="topbar">
        <div class="page-title">{{ pageTitle }}</div>
        <el-input class="search-box" placeholder="搜索代词、任务、健身记录…" clearable>
          <template #suffix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-badge :value="3" class="bell">
          <el-icon :size="20"><Bell /></el-icon>
        </el-badge>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>

    <!-- 新增自定义菜单弹窗 -->
    <div v-if="addDialogVisible" class="modal-overlay" @click.self="addDialogVisible = false">
      <div class="modal ld-card">
        <div class="modal-title">新增自定义菜单</div>
        <div class="modal-row">
          <label>菜单名称</label>
          <el-input
            v-model="addForm.name"
            placeholder="如 RPT 接口测试用例平台"
            maxlength="64"
          />
        </div>
        <div class="modal-row">
          <label>网站地址</label>
          <el-input
            v-model="addForm.url"
            placeholder="http://10.139.92.114:8098/#/login"
          />
        </div>
        <div class="modal-hint">
          点击新增的菜单后，该网站将以 iframe 嵌入当前系统内展示，不会跳转到新窗口。
        </div>
        <div class="modal-footer">
          <button class="ld-btn ld-btn-ghost" @click="addDialogVisible = false">取消</button>
          <button class="ld-btn ld-btn-primary" :disabled="submitting" @click="onSubmitAdd">
            {{ submitting ? "保存中…" : "保存并打开" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Search,
  Bell,
  HomeFilled,
  Folder,
  List,
  Calendar,
  Monitor,
  Trophy,
  Collection,
  Reading,
  Sunny,
  Plus,
  Link,
  CircleClose
} from "@element-plus/icons-vue";
import { useCustomMenus } from "@/composables/useCustomMenus";
import type { CustomMenu } from "@/api/customMenu";

defineOptions({ name: "MainLayout" });

const route = useRoute();
const router = useRouter();
const pageTitle = computed(() => (route.meta.title as string) || "LifeDesk");

const navItems = [
  { path: "/dashboard", label: "首页", icon: HomeFilled },
  { path: "/project", label: "项目管理", icon: Folder },
  { path: "/task", label: "任务工单", icon: List },
  { path: "/schedule", label: "日程会议", icon: Calendar },
  { path: "/monitor", label: "系统监控", icon: Monitor },
  { path: "/skill", label: "Skills 工坊", icon: Trophy },
  { path: "/bookmark", label: "收藏与笔记", icon: Collection },
  { path: "/english", label: "英语提升", icon: Reading },
  { path: "/health", label: "个人健康", icon: Sunny }
];

// ===== 自定义菜单 =====
const { customMenus, loadCustomMenus, addCustomMenu, removeCustomMenu } = useCustomMenus();

// 判断某条自定义菜单是否是当前正在查看的（按 route.query.id 精确匹配，
// 避免 router-link 的 active-class 在 /custom 路径下对所有菜单同时激活）
function isCurrentMenu(m: CustomMenu): boolean {
  return route.path === "/custom" && route.query.id === String(m.id);
}

// 图标映射：后端存的是字符串图标名，前端解析为组件
function iconOf(icon?: string) {
  if (!icon) return Link;
  // 简单映射，扩展时再补
  const map: Record<string, any> = { Link };
  return map[icon] || Link;
}

// 新增菜单弹窗
const addDialogVisible = ref(false);
const submitting = ref(false);
const addForm = ref<{ name: string; url: string }>({ name: "", url: "" });

function openAddDialog() {
  addForm.value = { name: "", url: "" };
  addDialogVisible.value = true;
}

async function onSubmitAdd() {
  const name = addForm.value.name.trim();
  const url = addForm.value.url.trim();
  if (!name) {
    ElMessage.warning("请填写菜单名称");
    return;
  }
  if (!url) {
    ElMessage.warning("请填写网站地址");
    return;
  }
  if (!/^https?:\/\//i.test(url)) {
    ElMessage.warning("网站地址需以 http:// 或 https:// 开头");
    return;
  }
  submitting.value = true;
  try {
    await addCustomMenu({ name, url, sortNo: 0, icon: "Link" });
    ElMessage.success("已新增菜单");
    addDialogVisible.value = false;
    // 新增后自动跳转到该菜单的嵌入页
    const justAdded = customMenus.value.find((m) => m.name === name && m.url === url);
    if (justAdded && justAdded.id) {
      router.push({
        path: "/custom",
        query: { id: String(justAdded.id), name: justAdded.name, url: justAdded.url }
      });
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false;
  }
}

async function onDeleteMenu(m: CustomMenu) {
  try {
    await ElMessageBox.confirm(`确定删除菜单「${m.name}」？`, "提示", { type: "warning" });
    await removeCustomMenu(m.id!);
    ElMessage.success("已删除");
    // 如果当前正在看这条菜单的嵌入页，跳回首页
    if (route.path === "/custom" && route.query.id === String(m.id)) {
      router.replace("/dashboard");
    }
  } catch {
    /* 取消或拦截器已提示 */
  }
}

onMounted(() => {
  loadCustomMenus();
});
</script>

<style scoped lang="scss">
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

// 侧栏：半透明深紫 + 玻璃模糊，让右侧页面渐变光斑透过
.sidebar {
  width: 240px;
  flex-shrink: 0;
  background: $bg-sidebar;
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-right: 1px solid $border-glass;
  display: flex;
  flex-direction: column;
  padding: 18px 0;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 22px 22px;
}
.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, $accent, $accent-light);
  font-weight: 700;
  font-size: 18px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(167, 139, 250, 0.35);
}
.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: $text-primary;
}

.nav-group {
  padding: 8px 12px;
  flex: 1;
  overflow-y: auto;
}
.nav-title {
  padding: 8px 12px 4px;
  font-size: 11px;
  color: $text-tertiary;
  letter-spacing: 1px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  font-size: 14px;
  color: $text-secondary;
  border-radius: 10px;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.15s;
  margin-bottom: 2px;
  width: 100%;
  background: transparent;
  border: 1px solid transparent;
  text-align: left;
  font-family: inherit;
  &:hover {
    background: rgba(167, 139, 250, 0.08);
    color: $text-primary;
  }
}
.nav-item-active {
  background: rgba(167, 139, 250, 0.16);
  color: $accent-light;
  border: 1px solid $border-accent;
}

// 自定义菜单项：名称可截断，hover 时显示删除图标
.custom-nav-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.custom-nav-del {
  color: $text-tertiary;
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  &:hover {
    color: #f87171;
  }
}
.custom-nav-item:hover .custom-nav-del {
  opacity: 1;
}

// 新增菜单按钮：与上面 nav-item 同宽同高，风格保持一致
.nav-add {
  border: 1px dashed $border-accent;
  color: $accent-light;
  &:hover {
    background: rgba(167, 139, 250, 0.12);
    color: $accent-light;
    border-color: $accent;
  }
}

.health-mini {
  margin: 6px 14px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid $border-glass;
  border-radius: $radius-sm;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}
.mini-title {
  font-size: 11px;
  color: $text-tertiary;
  margin-bottom: 6px;
  letter-spacing: 1px;
}
.mini-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  line-height: 1.7;
  color: $text-secondary;
}

.user-area {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 22px 6px;
  border-top: 1px solid $border-glass;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, $accent, $accent-light);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(167, 139, 250, 0.3);
}
.nickname {
  font-size: 14px;
  color: $text-primary;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
// 顶栏：玻璃风格（让 content 的渐变光斑透过顶栏）
.topbar {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 22px;
  background: rgba(11, 8, 23, 0.55);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid $border-glass;
}
.page-title {
  font-size: 17px;
  font-weight: 600;
  color: $text-primary;
  min-width: 120px;
}
.search-box {
  flex: 1;
  max-width: 520px;
}
.bell {
  margin-left: auto;
  cursor: pointer;
  color: $text-secondary;
}
.content {
  flex: 1;
  overflow-y: auto;
  padding: 22px;
}

/* 新增菜单弹窗 */
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
.modal-hint {
  font-size: 12px;
  color: $text-tertiary;
  line-height: 1.6;
  margin-bottom: 8px;
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

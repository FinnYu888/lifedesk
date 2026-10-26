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

        <!-- 自定义菜单：用户自行添加的外部网站（iframe 嵌入当前系统）
             点击打开为 tab，多个 tab 不互相覆盖；已打开的菜单再点 → 自动定位到该 tab -->
        <div class="nav-title">自定义</div>
        <div
          v-for="m in customMenus"
          :key="m.id"
          class="nav-item custom-nav-item"
          :class="{ 'nav-item-active': isTabActiveFor(m) }"
          @click="onCustomMenuClick(m)"
        >
          <el-icon><component :is="iconOf(m.icon)" /></el-icon>
          <span class="custom-nav-label">{{ m.name }}</span>
          <el-icon class="custom-nav-del" @click.stop="onDeleteMenu(m)"><CircleClose /></el-icon>
        </div>

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
        <!-- 主题切换：深紫玻璃(glass) ⇄ 科技HUD(tech)，选择持久化 localStorage -->
        <button
          class="theme-toggle"
          :title="theme === 'tech' ? '切换到深紫玻璃风' : '切换到科技 HUD 风'"
          @click="toggleTheme"
        >
          <el-icon :size="18"><component :is="theme === 'tech' ? Cpu : Sunny" /></el-icon>
          <span>{{ theme === "tech" ? "科技 HUD" : "液态玻璃" }}</span>
        </button>
        <el-badge :value="3" class="bell">
          <el-icon :size="20"><Bell /></el-icon>
        </el-badge>
      </header>

      <!-- 自定义菜单 Tab 栏：仅当有打开的 tab 时显示。
           每个自定义菜单点击后开一个 tab，互不覆盖；点 tab 切换激活；点 x 关闭 -->
      <div v-if="tabs.length > 0" class="menu-tab-bar">
        <div class="menu-tabs">
          <div
            v-for="t in tabs"
            :key="t.id"
            class="menu-tab"
            :class="{ active: t.id === activeTabId && tabMode }"
            :title="t.name + ' · ' + t.url"
            @click="activateTab(t.id)"
          >
            <el-icon class="tab-icon"><component :is="iconOf(t.icon)" /></el-icon>
            <span class="tab-label">{{ t.name }}</span>
            <el-icon class="tab-close" @click.stop="closeTab(t.id)"><Close /></el-icon>
          </div>
        </div>
      </div>

      <main class="content">
        <!-- Tab 视图：自定义菜单 iframe 多实例（每个 tab 独立 iframe，互不重载） -->
        <div v-show="tabMode && tabs.length > 0" class="tab-view">
          <!-- 当前激活 tab 的工具条 -->
          <div class="tab-toolbar">
            <div class="tab-toolbar-info">
              <el-icon class="tbi-icon"><Link /></el-icon>
              <span class="tbi-name">{{ activeTab?.name || "" }}</span>
              <span class="tbi-url">{{ activeTab?.url || "" }}</span>
            </div>
            <div class="tab-toolbar-actions">
              <button
                class="ld-btn ld-btn-ghost"
                :disabled="!activeTab"
                @click="openTabExternal"
              >
                <el-icon><TopRight /></el-icon> 新窗口
              </button>
              <button
                class="ld-btn ld-btn-ghost"
                :disabled="!activeTab"
                @click="onTabEdit"
              >
                <el-icon><Edit /></el-icon> 编辑
              </button>
              <button
                class="ld-btn ld-btn-ghost danger"
                :disabled="!activeTab"
                @click="onTabDelete"
              >
                <el-icon><Delete /></el-icon> 删除
              </button>
            </div>
          </div>
          <!-- iframe 多实例：用 v-show 切换显隐，不销毁，保留登录态与滚动位置 -->
          <div class="tab-frames">
            <div
              v-for="t in tabs"
              :key="t.id"
              class="tab-frame-item"
              v-show="t.id === activeTabId"
            >
              <div v-if="!iframeLoaded[t.id]" class="iframe-loading">
                <el-icon class="loading-icon" :size="28"><Loading /></el-icon>
                <span>正在加载 {{ t.name }}…</span>
                <span class="loading-hint">如长时间无响应，可点击「新窗口」打开</span>
              </div>
              <iframe
                :src="t.url"
                frameborder="0"
                allow="fullscreen; clipboard-read; clipboard-write; encrypted-media; autoplay; picture-in-picture"
                sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox allow-downloads allow-modals allow-presentation"
                referrerpolicy="no-referrer"
                @load="onIframeLoad(t.id)"
              />
            </div>
          </div>
        </div>
        <!-- 普通 router-view：非 tab 模式或无 tab 时显示。
             外包一层 wrapper 以便在 tab 视图模式下取消 padding（让 iframe 全屏铺满） -->
        <div v-show="!tabMode || tabs.length === 0" class="router-view-wrap">
          <router-view />
        </div>
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
import { ref, computed, watch, onMounted } from "vue";
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
  CircleClose,
  Close,
  Edit,
  Delete,
  TopRight,
  Loading,
  Cpu
} from "@element-plus/icons-vue";
import { useCustomMenus } from "@/composables/useCustomMenus";
import { useMenuTabs } from "@/composables/useMenuTabs";
import { useTheme } from "@/composables/useTheme";
import type { CustomMenu } from "@/api/customMenu";

defineOptions({ name: "MainLayout" });

const route = useRoute();
const router = useRouter();
// ===== 主题切换 =====
const { theme, toggleTheme } = useTheme();
const pageTitle = computed(() => {
  // tab 模式下优先显示当前 tab 名
  if (tabMode.value && activeTab.value) return activeTab.value.name;
  return (route.meta.title as string) || "LifeDesk";
});

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
const { customMenus, loadCustomMenus, addCustomMenu, removeCustomMenu, editCustomMenu } =
  useCustomMenus();

// ===== 自定义菜单 Tab 状态（模块级单例，跨组件共享） =====
const {
  tabs,
  activeTabId,
  activeTab,
  tabMode,
  openTab,
  closeTab,
  activateTab,
  removeTab
} = useMenuTabs();

// 每个 iframe 是否加载完成（按 tab id 索引，加载完成后保留 true，切换 tab 不重置）
const iframeLoaded = ref<Record<number, boolean>>({});

function onIframeLoad(id: number) {
  iframeLoaded.value[id] = true;
}

// 判断某条自定义菜单对应的 tab 是否处于激活状态（侧栏高亮用）
function isTabActiveFor(m: CustomMenu): boolean {
  return tabMode.value && activeTabId.value === m.id;
}

// 点击左侧自定义菜单：已打开 → 自动定位到该 tab；未打开 → 新增 tab 并定位
function onCustomMenuClick(m: CustomMenu) {
  openTab(m);
  // 重置该 iframe 的 loading（若是首次打开则进入 loading 态；已加载完成的不变）
  if (!(m.id! in iframeLoaded.value)) {
    iframeLoaded.value[m.id!] = false;
  }
}

// 点击普通导航项时，路由变化 → 自动退出 tab 视图，显示 router-view
// （tab 不关闭，下次点自定义菜单或 tab 标签时可再切回）
watch(
  () => route.path,
  (p) => {
    if (p !== "/custom") {
      tabMode.value = false;
    }
  }
);

// 图标映射：后端存的是字符串图标名，前端解析为组件
function iconOf(icon?: string) {
  if (!icon) return Link;
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
    // 新增后自动以 tab 方式打开该菜单
    const justAdded = customMenus.value.find((m) => m.name === name && m.url === url);
    if (justAdded && justAdded.id) {
      onCustomMenuClick(justAdded);
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
    // 同步关闭对应的 tab
    removeTab(m.id!);
    ElMessage.success("已删除");
  } catch {
    /* 取消或拦截器已提示 */
  }
}

// ===== Tab 工具条操作 =====
function openTabExternal() {
  if (activeTab.value?.url) {
    window.open(activeTab.value.url, "_blank", "noopener,noreferrer");
  }
}

function onTabEdit() {
  if (!activeTab.value) return;
  const id = activeTab.value.id;
  ElMessageBox.prompt("请输入菜单名称", "编辑菜单", {
    inputValue: activeTab.value.name,
    inputPattern: /.+/,
    inputErrorMessage: "名称不能为空"
  })
    .then(async ({ value: name }) => {
      ElMessageBox.prompt("请输入网站地址（URL）", "网站地址", {
        inputValue: activeTab.value!.url,
        inputPattern: /^https?:\/\/.+/,
        inputErrorMessage: "请输入合法的 http(s) 地址"
      })
        .then(async ({ value: url }) => {
          await editCustomMenu(id, { name, url });
          // 同步 tab 信息（openTab 会把最新 name/url 写入已存在的 tab）
          const updated = customMenus.value.find((m) => m.id === id);
          if (updated) {
            // url 变了 → 该 iframe 需要重新进入 loading 态
            iframeLoaded.value[id] = false;
            openTab(updated);
          }
          ElMessage.success("已保存");
        })
        .catch(() => {});
    })
    .catch(() => {});
}

async function onTabDelete() {
  if (!activeTab.value) return;
  const id = activeTab.value.id;
  const name = activeTab.value.name;
  try {
    await ElMessageBox.confirm(`确定删除菜单「${name}」？`, "提示", { type: "warning" });
    await removeCustomMenu(id);
    removeTab(id);
    ElMessage.success("已删除");
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

// 主题切换按钮：跟随主题变量自动换肤
.theme-toggle {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-family: inherit;
  color: $text-secondary;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid $border-glass;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  &:hover {
    color: $accent-light;
    border-color: $border-accent;
    background: rgba(167, 139, 250, 0.1);
    box-shadow: 0 0 12px rgba(167, 139, 250, 0.25);
  }
}
html[data-theme="tech"] .theme-toggle {
  background: rgba(34, 211, 238, 0.08);
  border-color: rgba(34, 211, 238, 0.35);
  color: #8fb8dc;
  &:hover {
    color: #67e8f9;
    border-color: rgba(34, 211, 238, 0.7);
    background: rgba(34, 211, 238, 0.15);
    box-shadow: 0 0 14px rgba(34, 211, 238, 0.4);
  }
}
// bell 不再需要 margin-left:auto（theme-toggle 接管了推右）
.bell {
  margin-left: 0;
}
.content {
  flex: 1;
  overflow-y: auto;
  padding: 0;             /* 取消外层 padding，由内部 .router-view-wrap / .tab-view 自管理 */
  display: flex;
  flex-direction: column;
}

/* 普通视图：保持原有 22px 内边距，让 dashboard 等页面布局不变 */
.router-view-wrap {
  flex: 1;
  padding: 22px;
  overflow-y: auto;
}

/* ========== 自定义菜单 Tab 栏 ========== */
.menu-tab-bar {
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  padding: 0 18px;
  background: rgba(11, 8, 23, 0.55);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  border-bottom: 1px solid $border-glass;
  /* 兼容横向溢出：tab 多时可横向滚动 */
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
  &::-webkit-scrollbar { height: 4px; }
  &::-webkit-scrollbar-thumb { background: rgba(167, 139, 250, 0.3); border-radius: 2px; }
}
.menu-tabs {
  display: flex;
  align-items: stretch;
  gap: 4px;
  padding: 6px 0;
}
.menu-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px 6px 10px;
  font-size: 13px;
  color: $text-secondary;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid $border-glass;
  border-radius: 8px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  user-select: none;
  &:hover {
    background: rgba(167, 139, 250, 0.12);
    color: $text-primary;
    border-color: $border-accent;
  }
}
.menu-tab.active {
  background: linear-gradient(135deg, rgba(167, 139, 250, 0.32), rgba(196, 181, 253, 0.18));
  color: $accent-light;
  border-color: $border-accent;
  box-shadow: 0 0 14px rgba(167, 139, 250, 0.28), inset 0 1px 0 rgba(255, 255, 255, 0.12);
}
.tab-icon {
  font-size: 14px;
  opacity: 0.85;
}
.tab-label {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tab-close {
  margin-left: 4px;
  font-size: 13px;
  color: $text-tertiary;
  border-radius: 4px;
  padding: 1px;
  transition: all 0.15s;
  &:hover {
    color: #f87171;
    background: rgba(248, 113, 113, 0.15);
  }
}

/* ========== Tab 视图：iframe 多实例 ========== */
.tab-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* 让 iframe 嵌入区填满 content 高度 */
  min-height: 0;
}
.tab-toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 18px;
  background: rgba(20, 14, 38, 0.5);
  border-bottom: 1px solid $border-glass;
}
.tab-toolbar-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.tbi-icon {
  color: $accent-light;
  font-size: 15px;
}
.tbi-name {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  white-space: nowrap;
}
.tbi-url {
  font-size: 12px;
  color: $text-tertiary;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tab-toolbar-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.tab-frames {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: #fff;
  min-height: 0;
}
.tab-frame-item {
  position: absolute;
  inset: 0;
  iframe {
    width: 100%;
    height: 100%;
    border: none;
    background: #fff;
  }
}
.iframe-loading {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: $text-tertiary;
  font-size: 13px;
  background: $bg-card;
  z-index: 2;
}
.loading-icon {
  color: $accent-light;
  animation: tab-spin 1.1s linear infinite;
}
.loading-hint {
  font-size: 11px;
  color: $text-tertiary;
  margin-top: 4px;
}
@keyframes tab-spin {
  to { transform: rotate(360deg); }
}

/* Tab 工具条按钮复用 .ld-btn，额外加 .danger 修饰 */
.tab-toolbar-actions .ld-btn.danger:hover {
  color: #f87171;
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

import { ref, computed } from "vue";

/**
 * 菜单「多 Tab」共享状态（内置工作台菜单 + 自定义 iframe 菜单统一管理）。
 *
 * <p>三类 tab：</p>
 * <ul>
 *   <li>custom：自定义菜单，iframe 嵌入，id 为后端菜单 id（正数），tabMode=true 显示 iframe 区</li>
 *   <li>builtin：工作台内置菜单（首页/项目管理等），id 为路由 path 的负数 hash，激活时 router 跳转、显示 router-view</li>
 *   <li>app：自定义本地应用，窗口画面嵌入展示，id 为应用 id + APP_ID_OFFSET 偏移（避免与菜单 id 冲突）</li>
 * </ul>
 *
 * <p>模块级单例（multiple components import the same instance），与 Pinia store 同理。</p>
 */
export interface MenuTab {
  id: number;
  name: string;
  /** custom 类型：iframe 地址 */
  url?: string;
  /** builtin 类型：路由 path */
  path?: string;
  /** custom: 图标名字符串；builtin: 图标组件 */
  icon?: any;
  type?: "custom" | "builtin" | "app";
}

const tabs = ref<MenuTab[]>([]);
const activeTabId = ref<number | null>(null);
/** 是否处于 tab 视图模式（true=显示自定义菜单 iframe 区；false=显示 router-view） */
const tabMode = ref(false);

/** 应用 tab id 偏移量：菜单 id 从 1 递增，应用 tab id = 100000 + appId，天然不冲突 */
const APP_ID_OFFSET = 100000;

/** 由路由 path 生成稳定的负数 tab id（与后端正数菜单 id 天然不冲突） */
function builtinTabId(path: string): number {
  let h = 0;
  for (let i = 0; i < path.length; i++) h = ((h << 5) - h + path.charCodeAt(i)) | 0;
  return -Math.abs(h || 1);
}

/** 根据当前激活 tab 的类型决定视图模式：builtin → router-view；custom → iframe 区 */
function applyMode(): void {
  const t = tabs.value.find((x) => x.id === activeTabId.value);
  tabMode.value = !!t && t.type !== "builtin";
}

/** 把一个自定义本地应用「打开」为 tab：已开 → 激活；未开 → 追加并激活 */
function openAppTab(app: { id: number; name: string }): void {
  const id = APP_ID_OFFSET + app.id;
  const exist = tabs.value.find((t) => t.id === id);
  if (!exist) {
    tabs.value.push({ id, name: app.name, type: "app" });
  } else {
    exist.name = app.name;
  }
  activeTabId.value = id;
  applyMode();
}

/** 应用 tab id → 应用真实 id（MainLayout 渲染截图流时换算） */
function appRealId(tabId: number): number {
  return tabId - APP_ID_OFFSET;
}

/** 打开/激活内置菜单 tab：已开 → 激活；未开 → 追加并激活（不重复开） */
function openBuiltinTab(name: string, path: string, icon?: any): void {
  const id = builtinTabId(path);
  const exist = tabs.value.find((t) => t.id === id);
  if (!exist) {
    tabs.value.push({ id, name, path, icon, type: "builtin" });
  } else {
    exist.name = name;
    exist.icon = icon;
  }
  activeTabId.value = id;
  applyMode();
}

/** 把一条自定义菜单「打开」为 tab：已开 → 激活；未开 → 追加并激活 */
function openTab(menu: {
  id?: number;
  name: string;
  url: string;
  icon?: string;
}): void {
  if (!menu.id) return;
  const exist = tabs.value.find((t) => t.id === menu.id);
  if (!exist) {
    tabs.value.push({
      id: menu.id,
      name: menu.name,
      url: menu.url,
      icon: menu.icon,
      type: "custom"
    });
  } else {
    // 已存在则同步最新 name/url（菜单可能被编辑过）
    exist.name = menu.name;
    exist.url = menu.url;
    exist.icon = menu.icon;
  }
  activeTabId.value = menu.id;
  applyMode();
}

/** 关闭某个 tab：自动激活相邻 tab；若是当前激活则切到下一个/上一个 */
function closeTab(id: number): void {
  const idx = tabs.value.findIndex((t) => t.id === id);
  if (idx < 0) return;
  tabs.value.splice(idx, 1);
  if (activeTabId.value === id) {
    if (tabs.value.length === 0) {
      activeTabId.value = null;
      tabMode.value = false; // 没有可显示的 tab，退出 tab 视图
    } else {
      // 优先激活右边那张，没有则左边那张
      const nextIdx = Math.min(idx, tabs.value.length - 1);
      activeTabId.value = tabs.value[nextIdx].id;
      applyMode();
    }
  }
}

/** 仅切换激活 tab（点 tab 标签时用） */
function activateTab(id: number): void {
  if (tabs.value.find((t) => t.id === id)) {
    activeTabId.value = id;
    applyMode();
  }
}

/** 清空所有 tab（登出时清理） */
function clearAll(): void {
  tabs.value = [];
  activeTabId.value = null;
  tabMode.value = false;
}

/** 删除菜单后同步清理对应 tab */
function removeTab(id: number): void {
  closeTab(id);
}

const activeTab = computed<MenuTab | null>(() => {
  if (activeTabId.value == null) return null;
  return tabs.value.find((t) => t.id === activeTabId.value) || null;
});

export function useMenuTabs() {
  return {
    tabs,
    activeTabId,
    activeTab,
    tabMode,
    openTab,
    openBuiltinTab,
    openAppTab,
    appRealId,
    closeTab,
    activateTab,
    clearAll,
    removeTab
  };
}

import { ref, computed } from "vue";
import type { CustomMenu } from "@/api/customMenu";

/**
 * 自定义菜单的「多 Tab」共享状态。
 *
 * <p>背景：之前所有自定义菜单都走 /custom 路由，点击不同菜单会让 router-view
 * 的单一实例被新 URL 覆盖，丢失之前的页面状态（登录态、滚动位置等）。</p>
 *
 * <p>方案：不再依赖 router，而是用 tabs 列表 + activeTabId 自管理。
 * 每个已打开的菜单对应一个 tab，对应一份独立的 iframe DOM，v-show 切换显隐，
 * iframe 不重载 → 登录态保留、滚动位置保留。</p>
 *
 * <p>这是模块级单例（multiple components import the same instance），
 * 与 Pinia store 同理，但不引入额外依赖。</p>
 */
export interface MenuTab {
  id: number;
  name: string;
  url: string;
  icon?: string;
}

const tabs = ref<MenuTab[]>([]);
const activeTabId = ref<number | null>(null);
/** 是否处于 tab 视图模式（true=显示 tab iframe 列表；false=显示 router-view） */
const tabMode = ref(false);

/** 把一条自定义菜单「打开」为 tab：已开 → 激活；未开 → 追加并激活 */
function openTab(menu: CustomMenu): void {
  if (!menu.id) return;
  const exist = tabs.value.find((t) => t.id === menu.id);
  if (!exist) {
    tabs.value.push({
      id: menu.id,
      name: menu.name,
      url: menu.url,
      icon: menu.icon
    });
  } else {
    // 已存在则同步最新 name/url（菜单可能被编辑过）
    exist.name = menu.name;
    exist.url = menu.url;
    exist.icon = menu.icon;
  }
  activeTabId.value = menu.id;
  tabMode.value = true;
}

/** 关闭某个 tab：自动激活相邻 tab；若是当前激活则切到下一个/上一个 */
function closeTab(id: number): void {
  const idx = tabs.value.findIndex((t) => t.id === id);
  if (idx < 0) return;
  tabs.value.splice(idx, 1);
  if (activeTabId.value === id) {
    if (tabs.value.length === 0) {
      activeTabId.value = null;
      tabMode.value = false;  // 没有可显示的 tab，退出 tab 视图
    } else {
      // 优先激活右边那张，没有则左边那张
      const nextIdx = Math.min(idx, tabs.value.length - 1);
      activeTabId.value = tabs.value[nextIdx].id;
      tabMode.value = true;
    }
  }
}

/** 仅切换激活 tab（点 tab 标签时用） */
function activateTab(id: number): void {
  if (tabs.value.find((t) => t.id === id)) {
    activeTabId.value = id;
    tabMode.value = true;
  }
}

/** 清空所有 tab（删除菜单后保持一致性，或登出时清理） */
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
    closeTab,
    activateTab,
    clearAll,
    removeTab
  };
}

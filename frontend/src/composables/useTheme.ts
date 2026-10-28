import { ref, watch } from "vue";

/**
 * 全局主题切换（模块级单例）。
 *
 * 主题通过 html[data-theme] 属性驱动，CSS 变量在 styles/index.scss 定义：
 *   glass（默认）→ 深紫液态玻璃
 *   tech         → 科技 HUD 风（深蓝底 + 青色霓虹）
 *   cream        → 奶白色（暖调浅色：奶白底 + 焦糖主色）
 *   doomsday     → 末日科幻（各菜单页面配专属实景背景图 + 暗色蒙版保证数据可读）
 *
 * 选择持久化到 localStorage（key: lifedesk-theme），刷新后恢复。
 */

export type ThemeName = "glass" | "tech" | "cream" | "doomsday";

const THEME_KEY = "lifedesk-theme";

/** 主题循环顺序：点击切换按钮依次轮换 */
const THEME_CYCLE: ThemeName[] = ["glass", "tech", "cream", "doomsday"];

/** 主题展示名（按钮文字用） */
export const THEME_LABEL: Record<ThemeName, string> = {
  glass: "液态玻璃",
  tech: "科技 HUD",
  cream: "奶白暖调",
  doomsday: "末日科幻"
};

function loadInitial(): ThemeName {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    if (saved && THEME_CYCLE.includes(saved as ThemeName)) return saved as ThemeName;
  } catch {
    /* localStorage 不可用时用默认 */
  }
  return "glass";
}

const theme = ref<ThemeName>(loadInitial());

/** 把主题同步到 <html data-theme="...">（CSS 侧按属性覆盖变量，glass 为 :root 默认） */
function applyThemeToDom(t: ThemeName): void {
  if (t === "glass") {
    delete document.documentElement.dataset.theme;
  } else {
    document.documentElement.dataset.theme = t;
  }
}

/**
 * 切换瞬间给 <html> 加 theme-switching 类（配合 index.scss 全局禁用过渡/毛玻璃），
 * 强制合成层一次性扁平重建，避免大量 backdrop-filter 层在硬件加速下同时失效，
 * 出现「切主题后页面数据不显示、刷新才恢复」的渲染问题；随后派发 resize
 * 让 ECharts 等图表组件立即重绘画布。
 */
function forceLayerRebuild(t: ThemeName): void {
  const root = document.documentElement;
  root.classList.add("theme-switching");
  applyThemeToDom(t);
  try {
    localStorage.setItem(THEME_KEY, t);
  } catch {
    /* ignore */
  }
  // 强制同步 reflow：新主题样式立即生效，不走逐属性过渡
  void document.body.offsetHeight;
  window.setTimeout(() => {
    root.classList.remove("theme-switching");
    // 通知所有监听窗口尺寸的组件（vue-echarts autoresize 等）重绘
    window.dispatchEvent(new Event("resize"));
  }, 120);
}

// 同步到 <html data-theme="...">：CSS 侧 [data-theme="tech"]/[data-theme="cream"] 覆盖变量，
// glass 是 :root 默认值，data-theme 留空即可
watch(
  theme,
  (t) => {
    forceLayerRebuild(t);
  },
  { immediate: true }
);

/** 循环切换：glass → tech → cream → glass */
function toggleTheme() {
  const idx = THEME_CYCLE.indexOf(theme.value);
  theme.value = THEME_CYCLE[(idx + 1) % THEME_CYCLE.length];
}

export function useTheme() {
  return { theme, toggleTheme, THEME_LABEL };
}

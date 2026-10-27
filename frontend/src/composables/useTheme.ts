import { ref, watch } from "vue";

/**
 * 全局主题切换（模块级单例）。
 *
 * 主题通过 html[data-theme] 属性驱动，CSS 变量在 styles/index.scss 定义：
 *   glass（默认）→ 深紫液态玻璃
 *   tech         → 科技 HUD 风（深蓝底 + 青色霓虹）
 *   cream        → 奶白色（暖调浅色：奶白底 + 焦糖主色）
 *
 * 选择持久化到 localStorage（key: lifedesk-theme），刷新后恢复。
 */

export type ThemeName = "glass" | "tech" | "cream";

const THEME_KEY = "lifedesk-theme";

/** 主题循环顺序：点击切换按钮依次轮换 */
const THEME_CYCLE: ThemeName[] = ["glass", "tech", "cream"];

/** 主题展示名（按钮文字用） */
export const THEME_LABEL: Record<ThemeName, string> = {
  glass: "液态玻璃",
  tech: "科技 HUD",
  cream: "奶白暖调"
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

// 同步到 <html data-theme="...">：CSS 侧 [data-theme="tech"]/[data-theme="cream"] 覆盖变量，
// glass 是 :root 默认值，data-theme 留空即可
watch(
  theme,
  (t) => {
    if (t === "glass") {
      delete document.documentElement.dataset.theme;
    } else {
      document.documentElement.dataset.theme = t;
    }
    try {
      localStorage.setItem(THEME_KEY, t);
    } catch {
      /* ignore */
    }
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

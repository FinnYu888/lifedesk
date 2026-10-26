import { ref, watch } from "vue";

/**
 * 全局主题切换（模块级单例）。
 *
 * 主题通过 html[data-theme] 属性驱动，CSS 变量在 styles/index.scss 定义：
 *   glass（默认）→ 深紫液态玻璃
 *   tech         → 科技 HUD 风（深蓝底 + 青色霓虹）
 *
 * 选择持久化到 localStorage（key: lifedesk-theme），刷新后恢复。
 */

export type ThemeName = "glass" | "tech";

const THEME_KEY = "lifedesk-theme";

function loadInitial(): ThemeName {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    if (saved === "tech" || saved === "glass") return saved;
  } catch {
    /* localStorage 不可用时用默认 */
  }
  return "glass";
}

const theme = ref<ThemeName>(loadInitial());

// 同步到 <html data-theme="...">：CSS 侧 [data-theme="tech"] 覆盖变量，
// glass 是 :root 默认值，data-theme 留空即可
watch(
  theme,
  (t) => {
    if (t === "tech") {
      document.documentElement.dataset.theme = "tech";
    } else {
      delete document.documentElement.dataset.theme;
    }
    try {
      localStorage.setItem(THEME_KEY, t);
    } catch {
      /* ignore */
    }
  },
  { immediate: true }
);

function toggleTheme() {
  theme.value = theme.value === "glass" ? "tech" : "glass";
}

export function useTheme() {
  return { theme, toggleTheme };
}

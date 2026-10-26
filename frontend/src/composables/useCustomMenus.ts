import { ref } from "vue";
import {
  fetchCustomMenus,
  createCustomMenu,
  updateCustomMenu,
  deleteCustomMenu,
  type CustomMenu
} from "@/api/customMenu";

/**
 * 自定义菜单的共享状态与 CRUD。
 *
 * <p>单一数据源：MainLayout（侧栏渲染 + 新增/编辑弹窗）与 CustomMenuView（iframe 嵌入页的编辑/删除按钮）
 * 共用同一份 customMenus，任何一处增删改都会反映到另一处。</p>
 *
 * <p>这是模块级单例（multiple components import the same instance），
 * 与 Pinia store 同理，但不引入额外依赖。</p>
 *
 * <p>注意：不在 composable 里调 useRouter().push —— navigateToMenu 已移除，
 * 由调用方在自己的 setup 上下文里用 router.push，避免 await 后丢当前实例。</p>
 */

const customMenus = ref<CustomMenu[]>([]);
const loaded = ref(false);

/** 载入列表（首次调用走网络，后续调用也会刷新） */
async function loadCustomMenus() {
  try {
    const res = await fetchCustomMenus();
    customMenus.value = ((res as any).data || []) as CustomMenu[];
    loaded.value = true;
  } catch {
    /* 拦截器已提示 */
  }
}

/** 按 id 查菜单 */
function findMenu(id: number | null | undefined): CustomMenu | undefined {
  if (id == null) return undefined;
  return customMenus.value.find((m) => m.id === id);
}

async function addCustomMenu(data: CustomMenu) {
  await createCustomMenu(data);
  await loadCustomMenus();
}

async function editCustomMenu(id: number, data: Partial<CustomMenu>) {
  await updateCustomMenu(id, data);
  await loadCustomMenus();
}

async function removeCustomMenu(id: number) {
  await deleteCustomMenu(id);
  await loadCustomMenus();
}

export function useCustomMenus() {
  return {
    customMenus,
    loaded,
    loadCustomMenus,
    findMenu,
    addCustomMenu,
    editCustomMenu,
    removeCustomMenu
  };
}


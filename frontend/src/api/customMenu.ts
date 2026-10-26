import { http } from "@/utils/http";

export interface CustomMenu {
  id?: number;
  name: string;
  /** 网站地址，点击菜单后通过 iframe 嵌入当前系统展示（不跳转新窗口） */
  url: string;
  /** 排序号，越小越靠前，缺省 0 */
  sortNo?: number;
  /** 图标枚举（Element Plus 图标名，可选，缺省用 Link） */
  icon?: string;
  createTime?: string;
  updateTime?: string;
}

/** 列表：按 sort_no 升序；keyword 模糊匹配 name / url */
export const fetchCustomMenus = (params?: { keyword?: string }) =>
  http.get<any, Result>("/custom-menu", { params });

/** 新建 */
export const createCustomMenu = (data: CustomMenu) =>
  http.post<any, Result>("/custom-menu", data);

/** 编辑 */
export const updateCustomMenu = (id: number, data: Partial<CustomMenu>) =>
  http.put<any, Result>(`/custom-menu/${id}`, data);

/** 删除 */
export const deleteCustomMenu = (id: number) =>
  http.delete<any, Result>(`/custom-menu/${id}`);

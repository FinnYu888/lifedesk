import { http } from "@/utils/http";

export interface Favorite {
  id?: number;
  name: string;
  value: string;
  type: "link" | "note";
  description?: string;
  createTime?: string;
  updateTime?: string;
}

/** 列表：type=link|note 可选；keyword 模糊匹配 name/value/description */
export const fetchFavorites = (params?: { type?: string; keyword?: string }) =>
  http.get<any, Result>("/favorite", { params });

/** 新建 */
export const createFavorite = (data: Favorite) => http.post<any, Result>("/favorite", data);

/** 编辑 */
export const updateFavorite = (id: number, data: Partial<Favorite>) =>
  http.put<any, Result>(`/favorite/${id}`, data);

/** 删除 */
export const deleteFavorite = (id: number) => http.delete<any, Result>(`/favorite/${id}`);

/** 统计：{ total, links, notes } */
export const fetchFavoriteStat = () => http.get<any, Result>("/favorite/stat");

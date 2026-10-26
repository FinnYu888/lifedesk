import { http } from "@/utils/http";

export type FavoriteEnv = "test" | "integration" | "prod";
export type CdsVersion = "3.0" | "5.0";

export interface Favorite {
  id?: number;
  name: string;
  value: string;
  type: "link" | "note";
  description?: string;
  /** 环境：test=测试 / integration=集成 / prod=生产（仅 type=link 有意义） */
  env?: FavoriteEnv | string;
  /** CDS 版本：3.0 / 5.0（仅 type=link 有意义，红绿区分避免选错） */
  cdsVersion?: CdsVersion | string;
  /** 网页截图 URL，可选；为空时前端兜底用公开截图服务 mShots 自动生成 */
  screenshot?: string;
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

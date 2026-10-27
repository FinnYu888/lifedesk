import { http } from "@/utils/http";

/** 后端统一返回结构 */
interface Result {
  code: number;
  msg: string;
  data: any;
}

/** 自定义本地应用 */
export interface NativeAppItem {
  id: number;
  name: string;
  appPath: string;
  args?: string;
  workDir?: string;
  sortOrder?: number;
}

/** 目录浏览项 */
export interface BrowseItem {
  name: string;
  path: string;
  dir: boolean;
}

export const fetchNativeApps = () => http.get<any, Result>("/native-app");

export const createNativeApp = (data: Partial<NativeAppItem>) =>
  http.post<any, Result>("/native-app", data);

export const updateNativeApp = (id: number, data: Partial<NativeAppItem>) =>
  http.put<any, Result>(`/native-app/${id}`, data);

export const deleteNativeApp = (id: number) =>
  http.delete<any, Result>(`/native-app/${id}`);

/** 启动应用（本机真实启动，不嵌入窗口） */
export const launchNativeApp = (id: number) =>
  http.post<any, Result>(`/native-app/${id}/launch`);

/** 应用图标地址（与桌面快捷方式一致，作为 img src） */
export const nativeAppIconUrl = (id: number) => `/api/native-app/${id}/icon`;

/** 浏览本地目录（dir 为空 = 盘符列表） */
export const browseLocalDir = (dir?: string) =>
  http.get<any, Result>("/native-app/browse", { params: { dir: dir || undefined } });

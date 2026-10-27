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

/** 启动应用（已运行则置前），返回 { pid, alreadyRunning } */
export const launchNativeApp = (id: number) =>
  http.post<any, Result>(`/native-app/${id}/launch`);

/** 结束应用进程 */
export const stopNativeApp = (id: number) =>
  http.post<any, Result>(`/native-app/${id}/stop`);

/** 应用窗口置前 */
export const focusNativeApp = (id: number) =>
  http.post<any, Result>(`/native-app/${id}/focus`);

/** 全部应用运行状态：{ [id]: { running, pid, hasWindow } } */
export const fetchNativeAppStatus = () =>
  http.get<any, Result>("/native-app/status");

/** 窗口实时画面地址（作为 img src 轮询） */
export const nativeAppScreenshotUrl = (id: number, nonce: number) =>
  `/api/native-app/${id}/screenshot?t=${nonce}`;

/** 浏览本地目录（dir 为空 = 盘符列表） */
export const browseLocalDir = (dir?: string) =>
  http.get<any, Result>("/native-app/browse", { params: { dir: dir || undefined } });

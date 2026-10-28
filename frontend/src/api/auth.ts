import { http, type Result } from "@/utils/http";

/**
 * 认证 API：登录 / 注册 / 登出
 */

export interface AuthInfo {
  token: string;
  userId: number;
  nickname: string;
}

export const login = (username: string, password: string) =>
  http.post<any, Result<AuthInfo>>("/auth/login", { username, password });

export const register = (username: string, password: string) =>
  http.post<any, Result<AuthInfo>>("/auth/register", { username, password });

export const logout = () => http.post<any, Result>("/auth/logout");

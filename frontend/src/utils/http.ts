import axios from "axios";
import { ElMessage } from "element-plus";

// axios 实例本身已支持 get/post/put/delete/request 方法
const http = axios.create({
  baseURL: "/api",
  timeout: 15000
});

// 请求拦截：附带认证令牌
http.interceptors.request.use(config => {
  const token = localStorage.getItem("lifedesk-token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 未认证：清除本地状态并回到登录页
function handleUnauthorized() {
  localStorage.removeItem("lifedesk-token");
  localStorage.removeItem("lifedesk-user");
  if (!location.hash.startsWith("#/login")) {
    location.hash = "#/login";
  }
}

http.interceptors.response.use(
  response => {
    const data = response.data;
    if (data && typeof data === "object" && "code" in data) {
      if (data.code !== 0) {
        ElMessage.error(data.msg || "请求失败");
        return Promise.reject(new Error(data.msg || "请求失败"));
      }
      return data;
    }
    return response.data;
  },
  error => {
    // 401：登录过期/未登录 → 静默跳登录页
    if (error?.response?.status === 401) {
      handleUnauthorized();
      return Promise.reject(error);
    }
    const msg = error?.response?.data?.msg || error.message || "网络异常";
    ElMessage.error(msg);
    return Promise.reject(error);
  }
);

export interface Result<T = any> {
  code: number;
  msg: string;
  data: T;
}

export { http };

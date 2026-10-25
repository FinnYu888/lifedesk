import axios from "axios";
import { ElMessage } from "element-plus";

// axios 实例本身已支持 get/post/put/delete/request 方法
const http = axios.create({
  baseURL: "/api",
  timeout: 15000
});

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

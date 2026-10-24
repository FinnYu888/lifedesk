import { http } from "@/utils/http";

export const fetchTaskList = (status?: string) =>
  http.get<any, Result>("/task", { params: { status } });

export const fetchTaskBoard = () => http.get<any, Result>("/task/board");
export const createTask = (data: object) => http.post<any, Result>("/task", data);
export const moveTask = (id: number, status: string) =>
  http.put<any, Result>(`/task/${id}/move`, null, { params: { status } });
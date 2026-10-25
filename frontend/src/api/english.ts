import { http } from "@/utils/http";

export interface EnglishTask {
  id?: number;
  name: string;
  category: string;
  dailyGoal?: string;
  targetDays?: number;
  totalCompleted?: number;
  streakDays?: number;
  lastDoneDate?: string;
  createTime?: string;
  updateTime?: string;
}

export const CATEGORY_OPTIONS = [
  { value: "vocabulary", label: "单词" },
  { value: "grammar", label: "语法" },
  { value: "listening", label: "听力" },
  { value: "speaking", label: "口语" },
  { value: "reading", label: "阅读" },
  { value: "writing", label: "写作" },
  { value: "other", label: "其他" }
];

/** 列表：category 可选；keyword 模糊匹配 name/daily_goal */
export const fetchEnglishTasks = (params?: { category?: string; keyword?: string }) =>
  http.get<any, Result>("/english/task", { params });

export const createEnglishTask = (data: EnglishTask) => http.post<any, Result>("/english/task", data);

export const updateEnglishTask = (id: number, data: Partial<EnglishTask>) =>
  http.put<any, Result>(`/english/task/${id}`, data);

export const deleteEnglishTask = (id: number) => http.delete<any, Result>(`/english/task/${id}`);

/** 今日打卡（幂等） */
export const completeEnglishTask = (id: number) =>
  http.post<any, Result>(`/english/task/${id}/complete`);

/** 统计：{ total_tasks, today_done, week_completed, avg_completion_rate, by_category } */
export const fetchEnglishStat = () => http.get<any, Result>("/english/stat");

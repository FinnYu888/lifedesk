import { http } from "@/utils/http";

// ===== 只读 =====
export const fetchHealthOverview = () => http.get<any, Result>("/health/overview");
export const fetchHealthStat = () => http.get<any, Result>("/health/stat");
export const recordHealth = (data: object) => http.post<any, Result>("/health/record", data);

// ===== 字典 =====
export const fetchExerciseDict = () => http.get<any, Result>("/health/exercise-dict");
export const fetchFoodDict = (category?: string) =>
  http.get<any, Result>("/health/food-dict", { params: category ? { category } : {} });

// ===== 录入闭环 =====
export interface WorkoutPayload {
  exerciseId?: number;
  exerciseName?: string;
  sets?: number;
  reps?: number;
  weightKg?: number;
  durationMin?: number;
}
export interface DietPayload {
  foodId?: number;
  foodName?: string;
  meal: "breakfast" | "lunch" | "dinner" | "snack";
  amountG?: number;
}
export const addWorkout = (data: WorkoutPayload) =>
  http.post<any, Result>("/health/workout", data);
export const addDiet = (data: DietPayload) =>
  http.post<any, Result>("/health/diet", data);
export const deleteWorkout = (id: number) =>
  http.delete<any, Result>(`/health/workout/${id}`);
export const deleteDiet = (id: number) =>
  http.delete<any, Result>(`/health/diet/${id}`);

// ===== 汇总 =====
export const fetchTodaySummary = () => http.get<any, Result>("/health/today-summary");
export const fetchTrend = () => http.get<any, Result>("/health/trend");

// ===== 身体指标（身高/体重/三围/体脂） =====
export interface BodyMetric {
  id?: number;
  recordDate?: string;
  heightCm?: number | null;
  weightKg?: number | null;
  bustCm?: number | null;
  waistCm?: number | null;
  hipCm?: number | null;
  bodyFat?: number | null;
}
export const fetchBodyMetrics = () => http.get<any, Result>("/health/body-metrics");
export const saveBodyMetric = (data: Partial<BodyMetric>) =>
  http.post<any, Result>("/health/body-metric", data);

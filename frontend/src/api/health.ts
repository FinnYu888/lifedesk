import { http } from "@/utils/http";

export const fetchHealthOverview = () => http.get<any, Result>("/health/overview");
export const fetchHealthStat = () => http.get<any, Result>("/health/stat");
export const recordHealth = (data: object) => http.post<any, Result>("/health/record", data);
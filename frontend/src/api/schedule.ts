import { http } from "@/utils/http";

export const fetchScheduleStat = () => http.get<any, Result>("/schedule/stat");
export const fetchScheduleToday = () => http.get<any, Result>("/schedule/today");
export const fetchScheduleWeek = () => http.get<any, Result>("/schedule/week");
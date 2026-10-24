import { http } from "@/utils/http";

export const fetchGreeting = () => http.get<any, Result>("/dashboard/greeting");
export const fetchModuleStat = () => http.get<any, Result>("/dashboard/module-stat");
export const fetchDashboardOverview = () => http.get<any, Result>("/dashboard/overview");
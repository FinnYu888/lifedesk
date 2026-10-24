import { http } from "@/utils/http";

export const fetchMonitorOverview = () => http.get<any, Result>("/monitor/overview");
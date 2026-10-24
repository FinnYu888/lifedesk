import { http } from "@/utils/http";

export const fetchProjectStat = () => http.get<any, Result>("/project/stat");
export const fetchProjectActive = () => http.get<any, Result>("/project/active");
export const fetchProjectMilestones = () => http.get<any, Result>("/project/milestones");
export const fetchProjectCommits = () => http.get<any, Result>("/project/commits");
export const fetchProjectOverview = () => http.get<any, Result>("/project/overview");
export const createProject = (data: object) => http.post<any, Result>("/project", data);
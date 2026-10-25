import { http } from "@/utils/http";

export const fetchProjectStat = () => http.get<any, Result>("/project/stat");
export const fetchProjectActive = () => http.get<any, Result>("/project/active");
export const fetchProjectMilestones = () => http.get<any, Result>("/project/milestones");
export const fetchProjectCommits = () => http.get<any, Result>("/project/commits");
export const fetchProjectOverview = () => http.get<any, Result>("/project/overview");
export const createProject = (data: object) => http.post<any, Result>("/project", data);
export const updateProject = (id: number, data: object) => http.put<any, Result>(`/project/${id}`, data);
export const deleteProject = (id: number) => http.delete<any, Result>(`/project/${id}`);
export const syncProject = (id: number) => http.post<any, Result>(`/project/${id}/sync`);
export const fetchProjectPrs = (id: number) => http.get<any, Result>(`/project/${id}/prs`);
export const fetchProjectReleases = (id: number) => http.get<any, Result>(`/project/${id}/releases`);

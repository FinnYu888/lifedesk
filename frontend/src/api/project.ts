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
/** 拉取项目已配置仓库的分支列表（编辑页「使用分支」下拉，用项目已保存的地址/账密） */
export const fetchProjectBranches = (id: number) => http.get<any, Result>(`/project/${id}/branches`);
/** 用表单临时输入的地址/账密拉取分支列表（新建项目时「使用分支」下拉） */
export const probeProjectBranches = (data: {
  gitPlatform?: string;
  gitUrl?: string;
  gitUsername?: string;
  gitPassword?: string;
}) => http.post<any, Result>("/project/branches", data);

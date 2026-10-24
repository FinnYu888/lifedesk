import { http } from "@/utils/http";

export const fetchSkillStat = () => http.get<any, Result>("/skill/stat");
export const fetchSkillList = () => http.get<any, Result>("/skill");
export const createSkill = (data: object) => http.post<any, Result>("/skill", data);
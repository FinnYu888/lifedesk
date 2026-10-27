import { http } from "@/utils/http";

/**
 * 每日目标（健康/英语通用）。
 *
 * 语义：修改每日目标 = 生成新里程碑（start_date=当天），进入下一阶段；
 * 同一天内重复修改原地更新，不产生重复里程碑。
 */
export interface DailyGoal {
  id?: number;
  module: string;
  scope: string;
  label?: string;
  goalValue: string;
  unit?: string;
  startDate?: string;
}

export interface DailyGoalBoard {
  current: DailyGoal[];
  milestones: DailyGoal[];
  stageCount?: number;
}

/** 查询某模块目标：当前生效 + 里程碑历史 */
export const fetchDailyGoals = (module: string) =>
  http.get<any, Result>("/daily-goal", { params: { module } });

/** 修改每日目标（生成新里程碑 / 当天内原地更新 / 值未变幂等） */
export const saveDailyGoal = (data: DailyGoal) => http.post<any, Result>("/daily-goal", data);

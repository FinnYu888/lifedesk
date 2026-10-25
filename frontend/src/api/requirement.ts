import { http } from "@/utils/http";

/** 我的需求：从第三方需求平台拉取 */
export interface Requirement {
  id: number;
  sprint: string;
  personDay: number;
  taskStatus: string;
  topic: string;
  taskDetail: string;
  product: string;
  tester: string;
  sourceId?: string;
}

/** 里程碑（按 sprint 聚合） */
export interface RequirementMilestone {
  id: number | null;
  projectId: number | null;
  name: string;
  progress: number;
  note: string;
  dueDate: string | null;
}

/** 需求详情：远程接口 developTask/getDetail?id=... 返回的原始对象 */
export interface RequirementDetail {
  id?: string;
  title?: string;
  taskNo?: string;
  content?: string; // HTML 含 img/p/ol/li/figure 等标签
  personDays?: number | string;
  productOwner?: string;
  productOwnerName?: string;
  status?: string;
  issueNo?: string;
  issueSubTitle?: string;
  issueStatus?: string;
  testUser?: string;
  testUserName?: string;
  sprintId?: string;
  sprintName?: string;
  versions?: string;
  deadlineTime?: string;
  startTime?: string;
  closeTime?: string;
  createTime?: string;
  createUser?: string;
  createUserName?: string;
  updateTime?: string;
  updateUser?: string;
  updateUserName?: string;
  scope?: string;
  urgency?: string;
  taskType?: string;
  project?: string;
  [key: string]: any; // 兼容远程返回的其它字段
}

/**
 * 拉取"我的需求"列表
 * @param sprint  可选 sprint 过滤（如 CDS20261025）
 * @param status  可选任务状态过滤：todo/doing/testing/pending/done/cancel
 * @param keyword 可选关键词，按 任务详情/关联话题/产品/测试人 模糊匹配
 */
export const fetchRequirements = (
  sprint?: string,
  status?: string,
  keyword?: string
) =>
  http.get<any, Result>("/project/requirements", {
    params: { sprint, status, keyword }
  });

/** 拉取需求详情（用户点击行触发，含 content HTML 含 img 标签） */
export const fetchRequirementDetail = (sourceId: string) =>
  http.get<any, Result>(`/project/requirements/${sourceId}/detail`);

/** 拉取里程碑进度（按 sprint 聚合，sprint 后 8 位即里程碑日期） */
export const fetchRequirementMilestones = () =>
  http.get<any, Result>("/project/milestones");

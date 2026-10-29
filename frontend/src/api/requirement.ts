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

/** authorization 设置状态（不返回 token 本身，避免泄漏） */
export interface RequirementAuthStatus {
  /** 是否已设置动态 token（前端输入的最新 authorization） */
  hasDynamicToken: boolean;
  /** 是否有配置文件兜底 token */
  hasFallbackToken: boolean;
  /** 当前是否有可用 token（动态或兜底） */
  hasEffectiveToken: boolean;
  /** 当前 token 来源：dynamic=前端动态设置 / fallback=配置文件兜底 / none=未设置 */
  source: "dynamic" | "fallback" | "none";
  /** 是否已持久化到本地文件（后端重启后仍生效） */
  persisted?: boolean;
}

/**
 * 拉取"我的需求"列表
 * @param sprint  可选 sprint 过滤（如 CDS20261025）
 * @param status  可选任务状态过滤：todo/doing/testing/pending/done/cancel
 * @param keyword 可选关键词，按 任务详情/关联话题/产品/测试人 模糊匹配
 */
// 防缓存三件套：_t 时间戳参数 + no-cache 请求头 + 30s 超时（后端需实时透传远程接口，保存鉴权后立即拉新数据）
export const fetchRequirements = (
  sprint?: string,
  status?: string,
  keyword?: string
) =>
  http.get<any, Result>("/project/requirements", {
    params: { sprint, status, keyword, _t: Date.now() },
    headers: { "Cache-Control": "no-cache", Pragma: "no-cache" },
    timeout: 30000
  });

/** 拉取需求详情（用户点击行触发，含 content HTML 含 img 标签） */
export const fetchRequirementDetail = (sourceId: string) =>
  http.get<any, Result>(`/project/requirements/${sourceId}/detail`);

/** 拉取里程碑进度（按 sprint 聚合，sprint 后 8 位即里程碑日期） */
// 单独设置 60s 超时：后端需串行调用 sprint/query + developTask/query + 每个 sprint 的 issue/query，耗时较长
export const fetchRequirementMilestones = () =>
  http.get<any, Result>("/project/milestones", {
    params: { _t: Date.now() },
    headers: { "Cache-Control": "no-cache", Pragma: "no-cache" },
    timeout: 60000
  });

/** 查询当前 authorization 设置状态（不返回 token 本身，避免泄漏） */
export const getRequirementAuthStatus = () =>
  http.get<any, Result>("/project/requirements/authorization/status");

/**
 * 设置最新 authorization token（用户在"我的需求"卡片输入后调用）。
 * 设置后立即对所有 fetch* 方法生效，并触发前端刷新"我的需求"+"里程碑进度"。
 * @param token 最新 JWT（不带 Bearer 前缀）；传空字符串则清空动态 token
 */
export const setRequirementAuthorization = (token: string) =>
  http.post<any, Result>("/project/requirements/authorization", { token });

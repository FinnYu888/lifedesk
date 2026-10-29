import { http } from "@/utils/http";

/** RPT 单条测试用例的最近运行状态 */
export interface RptCaseStat {
  id: number;
  name: string;
  caseType: string;
  lastStatus: string;
  lastFinishedAt: string;
}

/** RPT 单个业务模块的用例通过率统计 */
export interface RptModuleStat {
  bizModule: string;
  bizModuleLabel: string;
  totalCount: number;
  passedCount: number;
  failedCount: number;
  passRate: number;
  /** 模块下用例明细（最近运行状态），点击模块展开查看 */
  cases: RptCaseStat[];
}

/** 单个租户（盐田/蛇口）的用例通过率聚合 */
export interface RptTenantPassRate {
  tenantId: number;
  tenantName: string;
  success: boolean;
  error?: string;
  modules: RptModuleStat[];
}

/** 双租户用例通过率（盐田 29 + 蛇口 30）——后端需实时调 RPT 远程接口聚合，放宽到 30s */
export const fetchRptPassRate = () =>
  http.get<any, Result<RptTenantPassRate[]>>("/rpt-stats/pass-rate", {
    timeout: 30000,
    params: { _t: Date.now() }
  });

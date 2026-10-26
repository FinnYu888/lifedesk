import { http } from "@/utils/http";

/** RPT 单个业务模块的用例通过率统计 */
export interface RptModuleStat {
  bizModule: string;
  bizModuleLabel: string;
  totalCount: number;
  passedCount: number;
  failedCount: number;
  passRate: number;
}

/** 单个租户（盐田/蛇口）的用例通过率聚合 */
export interface RptTenantPassRate {
  tenantId: number;
  tenantName: string;
  success: boolean;
  error?: string;
  modules: RptModuleStat[];
}

/** 双租户用例通过率（盐田 29 + 蛇口 30） */
export const fetchRptPassRate = () => http.get<any, Result<RptTenantPassRate[]>>("/rpt-stats/pass-rate");

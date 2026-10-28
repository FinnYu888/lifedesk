import { http, type Result } from "@/utils/http";

/**
 * 外部监控源 API（系统监控页）
 * 当前支持 RocketMQ；后续新增 Redis / MySQL / S3 等在此扩展
 */
export const fetchRocketMqOverview = () => http.get<any, Result>("/ext-monitor/rocketmq/overview");

export const refreshRocketMqOverview = () => http.post<any, Result>("/ext-monitor/rocketmq/refresh");

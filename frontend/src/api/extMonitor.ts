import { http, type Result } from "@/utils/http";

/**
 * 外部监控源 API（系统监控页）
 * 当前支持 RocketMQ / Redis；后续新增 MySQL / S3 等在此扩展
 */
export const fetchRocketMqOverview = () => http.get<any, Result>("/ext-monitor/rocketmq/overview");

export const refreshRocketMqOverview = () => http.post<any, Result>("/ext-monitor/rocketmq/refresh");

export const fetchRedisOverview = () => http.get<any, Result>("/ext-monitor/redis/overview");

export const refreshRedisOverview = () => http.post<any, Result>("/ext-monitor/redis/refresh");

import { http, type Result } from "@/utils/http";

/**
 * 外部监控源 API（系统监控页）
 * 当前支持 RocketMQ / Redis / MySQL / OSS；后续新增数据源在此扩展
 */
export const fetchRocketMqOverview = () => http.get<any, Result>("/ext-monitor/rocketmq/overview");

export const refreshRocketMqOverview = () => http.post<any, Result>("/ext-monitor/rocketmq/refresh");

export const fetchRedisOverview = () => http.get<any, Result>("/ext-monitor/redis/overview");

export const refreshRedisOverview = () => http.post<any, Result>("/ext-monitor/redis/refresh");

export const fetchMysqlOverview = () => http.get<any, Result>("/ext-monitor/mysql/overview");

export const refreshMysqlOverview = () => http.post<any, Result>("/ext-monitor/mysql/refresh");

export const fetchOssOverview = () => http.get<any, Result>("/ext-monitor/oss/overview");

export const refreshOssOverview = () => http.post<any, Result>("/ext-monitor/oss/refresh");

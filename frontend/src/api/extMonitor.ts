import { http, type Result } from "@/utils/http";

/**
 * 外部监控源 API（系统监控页）
 * 当前支持 RocketMQ / Redis / MySQL / OSS；后续新增数据源在此扩展
 */

/** 当前活跃告警列表 + 数量（右上角闹铃徽标数据源） */
export const fetchAlerts = () => http.get<any, Result>("/ext-monitor/alerts");

/** Redis 24h 趋势采样（QPS / 内存 / 命中率 / 连接数，30s 一个点） */
export const fetchRedisTrend = () => http.get<any, Result>("/ext-monitor/redis/trend");

export const fetchRocketMqOverview = () => http.get<any, Result>("/ext-monitor/rocketmq/overview");

export const refreshRocketMqOverview = () => http.post<any, Result>("/ext-monitor/rocketmq/refresh");

export const fetchRedisOverview = () => http.get<any, Result>("/ext-monitor/redis/overview");

export const refreshRedisOverview = () => http.post<any, Result>("/ext-monitor/redis/refresh");

export const fetchMysqlOverview = () => http.get<any, Result>("/ext-monitor/mysql/overview");

export const refreshMysqlOverview = () => http.post<any, Result>("/ext-monitor/mysql/refresh");

export const fetchOssOverview = () => http.get<any, Result>("/ext-monitor/oss/overview");

export const refreshOssOverview = () => http.post<any, Result>("/ext-monitor/oss/refresh");

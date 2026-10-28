package com.lifedesk.extmonitor.alert;

import com.lifedesk.extmonitor.mysql.MysqlMonitorService;
import com.lifedesk.extmonitor.oss.OssMonitorService;
import com.lifedesk.extmonitor.redis.RedisMonitorService;
import com.lifedesk.extmonitor.rocketmq.RocketMqMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 外部监控告警评估器。
 *
 * <p>后台定时（30s）拉取各监控源总览（命中各自缓存，不产生额外采集压力），按规则评估并
 * 维护 {@link AlertCenter} 中的当前告警：</p>
 * <ul>
 *   <li>RocketMQ：Dashboard 不可达、Broker master 离线、消息积压、消费延迟超阈值
 *       （积压量 / 消费 TPS 估算最早未消费消息等待时长，默认超过 5 分钟告警；
 *       消费 TPS 为 0 且有积压视为消费停滞）。</li>
 *   <li>Redis：不可达 / 认证失败、连接被拒绝、阻塞客户端、内存碎片率过高。</li>
 *   <li>MySQL：不可达、任一业务库连接失败。</li>
 *   <li>OSS：端点不可达或所有桶不可访问。</li>
 * </ul>
 *
 * <p>同时每轮顺带采集一个 Redis 趋势采样点（QPS / 内存 / 命中率 / 连接数），
 * 供系统监控页 24h 性能走势图使用。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluateService {

    private final AlertCenter alertCenter;
    private final RocketMqMonitorService rocketMqMonitorService;
    private final RedisMonitorService redisMonitorService;
    private final MysqlMonitorService mysqlMonitorService;
    private final OssMonitorService ossMonitorService;

    /** 队列消费延迟告警阈值（秒）：最早未消费消息等待超过该值即告警，默认 5 分钟 */
    @Value("${ext-monitor.alert.lag-delay-seconds:300}")
    private long lagDelaySeconds;

    /** 触发延迟告警的每消费组最小积压量（避免 1 条消息抖动误报），默认 10 */
    @Value("${ext-monitor.alert.lag-delay-min-lag:10}")
    private long lagDelayMinLag;

    /** 每轮评估（fixedDelay：上一轮结束后 30s 再执行，采集均命中各源自带缓存） */
    @Scheduled(initialDelay = 15000, fixedDelay = 30000)
    public void evaluate() {
        try {
            evalRocketMq();
        } catch (Exception e) {
            log.warn("alert evaluate rocketmq failed: {}", e.getMessage());
        }
        try {
            evalRedis();
        } catch (Exception e) {
            log.warn("alert evaluate redis failed: {}", e.getMessage());
        }
        try {
            evalMysql();
        } catch (Exception e) {
            log.warn("alert evaluate mysql failed: {}", e.getMessage());
        }
        try {
            evalOss();
        } catch (Exception e) {
            log.warn("alert evaluate oss failed: {}", e.getMessage());
        }
    }

    // ==================== RocketMQ ====================

    private void evalRocketMq() {
        Map<String, Object> d = rocketMqMonitorService.overview();
        if (!Boolean.TRUE.equals(d.get("ok"))) {
            alertCenter.fire("rocketmq", "critical", "rocketmq:unreachable",
                    "RocketMQ Dashboard 不可达：" + str(d.get("errMsg")));
            alertCenter.resolve("rocketmq:broker-down");
            alertCenter.resolve("rocketmq:backlog");
            alertCenter.resolvePrefix("rocketmq:lag-delay:");
            return;
        }
        alertCenter.resolve("rocketmq:unreachable");

        Map<?, ?> cluster = (Map<?, ?>) d.get("cluster");
        boolean allActive = cluster != null && Boolean.TRUE.equals(cluster.get("allActive"));
        if (!allActive) {
            alertCenter.fire("rocketmq", "critical", "rocketmq:broker-down", "Broker master 节点离线");
        } else {
            alertCenter.resolve("rocketmq:broker-down");
        }

        long totalLag = cluster != null && cluster.get("totalLag") != null
                ? ((Number) cluster.get("totalLag")).longValue() : 0;
        if (totalLag > 0) {
            alertCenter.fire("rocketmq", "warning", "rocketmq:backlog",
                    "消息积压 " + totalLag + " 条" + topLagBrief(d));
        } else {
            alertCenter.resolve("rocketmq:backlog");
        }

        // 消费延迟阈值告警（按消费组动态 key，消失的消费组自动清理）
        Set<String> keep = new HashSet<>();
        Object topLagObj = d.get("topLag");
        if (topLagObj instanceof List) {
            for (Object o : (List<?>) topLagObj) {
                Map<?, ?> g = (Map<?, ?>) o;
                String group = str(g.get("group"));
                long lag = num(g.get("lag"));
                double tps = dbl(g.get("consumeTps"));
                if (group.isEmpty() || lag < lagDelayMinLag) {
                    continue;
                }
                long delaySec = tps > 0 ? Math.round(lag / tps) : Long.MAX_VALUE; // 无消费速率 = 停滞
                if (delaySec >= lagDelaySeconds) {
                    String key = "rocketmq:lag-delay:" + group;
                    keep.add(key);
                    if (tps > 0) {
                        alertCenter.fire("rocketmq", "warning", key, "消费组 " + group
                                + " 最早未消费消息已等待约 " + humanSec(delaySec)
                                + "（积压 " + lag + " 条，消费 TPS " + tps + "），超过阈值 "
                                + (lagDelaySeconds / 60) + " 分钟");
                    } else {
                        alertCenter.fire("rocketmq", "critical", key, "消费组 " + group
                                + " 消费停滞（无消费速率），积压 " + lag + " 条未消费");
                    }
                }
            }
        }
        alertCenter.retainPrefix("rocketmq:lag-delay:", keep);
    }

    // ==================== Redis ====================

    private void evalRedis() {
        Map<String, Object> d = redisMonitorService.overview();
        // 趋势采样（采集成功才记录，24h 滚动窗口）
        redisMonitorService.recordTrendPoint(d);

        if (!Boolean.TRUE.equals(d.get("ok"))) {
            alertCenter.fire("redis", "critical", "redis:unreachable",
                    "Redis 不可达：" + str(d.get("errMsg")));
            resolveAllRedisRules();
            return;
        }
        alertCenter.resolve("redis:unreachable");

        Map<?, ?> stats = (Map<?, ?>) d.get("stats");
        Map<?, ?> clients = (Map<?, ?>) d.get("clients");
        Map<?, ?> memory = (Map<?, ?>) d.get("memory");

        long rejected = stats != null && stats.get("rejectedConnections") != null
                ? ((Number) stats.get("rejectedConnections")).longValue() : 0;
        if (rejected > 0) {
            alertCenter.fire("redis", "warning", "redis:rejected-conn",
                    "Redis 存在 " + rejected + " 个被拒绝的连接（maxclients 不足或连接风暴）");
        } else {
            alertCenter.resolve("redis:rejected-conn");
        }

        long blocked = clients != null && clients.get("blocked") != null
                ? ((Number) clients.get("blocked")).longValue() : 0;
        if (blocked > 0) {
            alertCenter.fire("redis", "warning", "redis:blocked-clients",
                    "Redis 有 " + blocked + " 个阻塞客户端（BLPOP 等阻塞命令挂起）");
        } else {
            alertCenter.resolve("redis:blocked-clients");
        }

        double fragRatio = memory != null && memory.get("fragRatio") != null
                ? ((Number) memory.get("fragRatio")).doubleValue() : 0;
        if (fragRatio >= 1.5) {
            alertCenter.fire("redis", "warning", "redis:frag-ratio",
                    "Redis 内存碎片率 " + fragRatio + "，超过 1.5（内存利用率偏低，考虑 activedefrag）");
        } else {
            alertCenter.resolve("redis:frag-ratio");
        }
    }

    private void resolveAllRedisRules() {
        alertCenter.resolve("redis:rejected-conn");
        alertCenter.resolve("redis:blocked-clients");
        alertCenter.resolve("redis:frag-ratio");
    }

    // ==================== MySQL ====================

    private void evalMysql() {
        Map<String, Object> d = mysqlMonitorService.overview();
        long dbUp = d.get("dbUp") != null ? ((Number) d.get("dbUp")).longValue() : 0;
        long dbTotal = d.get("dbTotal") != null ? ((Number) d.get("dbTotal")).longValue() : 0;
        if (dbUp == 0) {
            alertCenter.fire("mysql", "critical", "mysql:unreachable",
                    "MySQL " + (dbTotal > 0 ? dbTotal + " 个库全部" : "") + "不可达"
                            + (d.get("errMsg") != null ? "：" + str(d.get("errMsg")) : ""));
            alertCenter.resolvePrefix("mysql:db-down:");
            return;
        }
        alertCenter.resolve("mysql:unreachable");

        Set<String> keep = new HashSet<>();
        Object dbsObj = d.get("databases");
        if (dbsObj instanceof List) {
            for (Object o : (List<?>) dbsObj) {
                Map<?, ?> item = (Map<?, ?>) o;
                if (Boolean.TRUE.equals(item.get("ok"))) {
                    continue;
                }
                String db = str(item.get("db"));
                String key = "mysql:db-down:" + db;
                keep.add(key);
                alertCenter.fire("mysql", "warning", key,
                        "MySQL 库 " + db + " 连接失败：" + str(item.get("errMsg")));
            }
        }
        alertCenter.retainPrefix("mysql:db-down:", keep);
    }

    // ==================== OSS ====================

    private void evalOss() {
        Map<String, Object> d = ossMonitorService.overview();
        if (!Boolean.TRUE.equals(d.get("ok"))) {
            alertCenter.fire("oss", "critical", "oss:unreachable",
                    "文件存储 OSS 不可达：" + str(d.get("errMsg")));
        } else {
            alertCenter.resolve("oss:unreachable");
        }
    }

    // ==================== 小工具 ====================

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0;
    }

    private static double dbl(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }

    /** 积压 Top 概要：取最大积压消费组拼入消息 */
    private static String topLagBrief(Map<String, Object> overview) {
        Object topLagObj = overview.get("topLag");
        if (topLagObj instanceof List && !((List<?>) topLagObj).isEmpty()) {
            Map<?, ?> top = (Map<?, ?>) ((List<?>) topLagObj).get(0);
            return "，最多：" + str(top.get("group")) + "（" + num(top.get("lag")) + " 条）";
        }
        return "";
    }

    /** 秒数转可读时长 */
    private static String humanSec(long sec) {
        if (sec >= 3600) {
            long h = sec / 3600, m = (sec % 3600) / 60;
            return h + "小时" + (m > 0 ? m + "分" : "");
        }
        long m = sec / 60, s = sec % 60;
        return (m > 0 ? m + "分" : "") + (s > 0 ? s + "秒" : "");
    }
}

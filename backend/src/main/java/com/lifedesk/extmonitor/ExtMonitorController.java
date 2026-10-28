package com.lifedesk.extmonitor;

import com.lifedesk.common.R;
import com.lifedesk.extmonitor.mysql.MysqlMonitorService;
import com.lifedesk.extmonitor.oss.OssMonitorService;
import com.lifedesk.extmonitor.redis.RedisMonitorService;
import com.lifedesk.extmonitor.rocketmq.RocketMqMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 外部监控源 Controller。
 *
 * <p>统一挂载在 /ext-monitor 下，按数据源分接口：
 * 当前支持 RocketMQ / Redis / MySQL / OSS 四类，后续可继续扩展。
 * 各源采集器位于 {@code extmonitor.*} 子包，互不影响。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/ext-monitor")
@RequiredArgsConstructor
public class ExtMonitorController {

    private final RocketMqMonitorService rocketMqMonitorService;
    private final RedisMonitorService redisMonitorService;
    private final MysqlMonitorService mysqlMonitorService;
    private final OssMonitorService ossMonitorService;

    /** RocketMQ 集群总览（缓存 30s） */
    @GetMapping("/rocketmq/overview")
    public R<Map<String, Object>> rocketMqOverview() {
        return R.ok(rocketMqMonitorService.overview());
    }

    /** RocketMQ 集群总览（强制刷新：清会话与缓存重新采集） */
    @PostMapping("/rocketmq/refresh")
    public R<Map<String, Object>> rocketMqRefresh() {
        return R.ok(rocketMqMonitorService.refresh());
    }

    /** Redis 指标总览（缓存 30s） */
    @GetMapping("/redis/overview")
    public R<Map<String, Object>> redisOverview() {
        return R.ok(redisMonitorService.overview());
    }

    /** Redis 指标总览（强制刷新） */
    @PostMapping("/redis/refresh")
    public R<Map<String, Object>> redisRefresh() {
        return R.ok(redisMonitorService.refresh());
    }

    /** MySQL 指标总览（缓存 30s） */
    @GetMapping("/mysql/overview")
    public R<Map<String, Object>> mysqlOverview() {
        return R.ok(mysqlMonitorService.overview());
    }

    /** MySQL 指标总览（强制刷新） */
    @PostMapping("/mysql/refresh")
    public R<Map<String, Object>> mysqlRefresh() {
        return R.ok(mysqlMonitorService.refresh());
    }

    /** OSS 指标总览（后台异步统计 + 长缓存，collecting=true 表示统计进行中） */
    @GetMapping("/oss/overview")
    public R<Map<String, Object>> ossOverview() {
        return R.ok(ossMonitorService.overview());
    }

    /** OSS 指标总览（触发后台重新统计） */
    @PostMapping("/oss/refresh")
    public R<Map<String, Object>> ossRefresh() {
        return R.ok(ossMonitorService.refresh());
    }
}

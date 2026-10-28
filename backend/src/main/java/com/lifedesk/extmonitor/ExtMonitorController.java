package com.lifedesk.extmonitor;

import com.lifedesk.common.R;
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
 * 当前支持 RocketMQ（/rocketmq/overview），后续可扩展 Redis / MySQL / S3 等。
 * 各源采集器位于 {@code extmonitor.*} 子包，互不影响。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/ext-monitor")
@RequiredArgsConstructor
public class ExtMonitorController {

    private final RocketMqMonitorService rocketMqMonitorService;

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
}

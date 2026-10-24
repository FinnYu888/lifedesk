package com.lifedesk.monitor.service;

import com.lifedesk.monitor.entity.Alert;
import com.lifedesk.monitor.entity.PerfMetric;
import com.lifedesk.monitor.entity.ServerStatus;
import com.lifedesk.monitor.mapper.AlertMapper;
import com.lifedesk.monitor.mapper.PerfMetricMapper;
import com.lifedesk.monitor.mapper.ServerStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统监控 Service。
 *
 * <p>对应 07_系统监控.md：服务统计、服务器状态列表、24h 性能折线。</p>
 *
 * @author LifeDesk
 */
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final ServerStatusMapper serverStatusMapper;
    private final AlertMapper alertMapper;
    private final PerfMetricMapper perfMetricMapper;

    /** 统计卡：在线服务 12 / 告警 1 / 平均延迟 18ms */
    public Map<String, Object> getStat() {
        long online = serverStatusMapper.selectCount(null);
        long alertCount = alertMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Alert>()
                        .eq(Alert::getStatus, "active"));
        Map<String, Object> stat = new HashMap<>();
        stat.put("online_services", online);
        stat.put("alerts", alertCount);
        stat.put("alerts_note", "队列消费延迟");
        stat.put("avg_latency", 18);
        stat.put("avg_latency_delta", -6);
        return stat;
    }

    public List<ServerStatus> listServers() {
        return serverStatusMapper.selectList(null);
    }

    public List<Alert> listAlerts() {
        return alertMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Alert>()
                        .eq(Alert::getStatus, "active"));
    }

    public List<PerfMetric> listPerf() {
        return perfMetricMapper.selectList(null);
    }

    public Map<String, Object> overview() {
        Map<String, Object> d = new HashMap<>();
        d.put("stat", getStat());
        d.put("servers", listServers());
        d.put("alerts", listAlerts());
        d.put("perf", listPerf());
        return d;
    }
}
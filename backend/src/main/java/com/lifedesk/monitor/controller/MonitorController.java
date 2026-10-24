package com.lifedesk.monitor.controller;

import com.lifedesk.common.R;
import com.lifedesk.monitor.service.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统监控 Controller。
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        return R.ok(monitorService.getStat());
    }

    @GetMapping("/servers")
    public R<List<com.lifedesk.monitor.entity.ServerStatus>> servers() {
        return R.ok(monitorService.listServers());
    }

    @GetMapping("/alerts")
    public R<List<com.lifedesk.monitor.entity.Alert>> alerts() {
        return R.ok(monitorService.listAlerts());
    }

    @GetMapping("/perf")
    public R<List<com.lifedesk.monitor.entity.PerfMetric>> perf() {
        return R.ok(monitorService.listPerf());
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(monitorService.overview());
    }
}
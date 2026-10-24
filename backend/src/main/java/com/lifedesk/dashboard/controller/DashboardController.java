package com.lifedesk.dashboard.controller;

import com.lifedesk.common.R;
import com.lifedesk.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首页 Dashboard Controller。
 *
 * <p>路由前缀 /api/dashboard，提供问候、模块六宫格、整体聚合三个接口。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/greeting")
    public R<Map<String, Object>> greeting() {
        return R.ok(dashboardService.greeting());
    }

    @GetMapping("/module-stat")
    public R<Map<String, Object>> moduleStat() {
        return R.ok(dashboardService.moduleStat());
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(dashboardService.overview());
    }
}
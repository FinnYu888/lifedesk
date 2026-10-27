package com.lifedesk.daily.controller;

import com.lifedesk.common.R;
import com.lifedesk.daily.service.DailyBriefingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 每日速览 Controller。
 *
 * <p>路由前缀 /api/daily，提供 GitHub 热门、NBA 资讯、头条热榜三类外部数据。
 * 拉取失败统一返回空列表（前端展示"暂无数据"，不报错弹窗）。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/daily")
@RequiredArgsConstructor
public class DailyBriefingController {

    private final DailyBriefingService dailyBriefingService;

    /**
     * GitHub 热门项目 Top 10。
     *
     * @param period day=过去24小时 / week=过去一周 / month=过去30天，默认 week
     */
    @GetMapping("/github")
    public R<List<Map<String, Object>>> githubTrending(
            @RequestParam(value = "period", defaultValue = "week") String period) {
        return R.ok(dailyBriefingService.githubTrending(period));
    }

    /** NBA 热点资讯（虎扑，前 15 条） */
    @GetMapping("/nba")
    public R<List<Map<String, Object>>> nbaNews() {
        return R.ok(dailyBriefingService.nbaNews());
    }

    /** 今日头条热榜（官方 hot-board，前 15 条） */
    @GetMapping("/toutiao")
    public R<List<Map<String, Object>>> toutiaoHot() {
        return R.ok(dailyBriefingService.toutiaoHot());
    }
}

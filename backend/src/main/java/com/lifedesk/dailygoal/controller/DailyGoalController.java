package com.lifedesk.dailygoal.controller;

import com.lifedesk.common.R;
import com.lifedesk.dailygoal.entity.DailyGoal;
import com.lifedesk.dailygoal.service.DailyGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 每日目标 Controller。
 *
 * <p>路由前缀 /api/daily-goal：
 * <ul>
 *   <li>GET  /daily-goal?module=health|english  当前目标 + 里程碑历史</li>
 *   <li>POST /daily-goal                        修改目标（生成新里程碑 / 当天内原地更新 / 幂等）</li>
 * </ul>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/daily-goal")
@RequiredArgsConstructor
public class DailyGoalController {

    private final DailyGoalService dailyGoalService;

    @GetMapping
    public R<Map<String, Object>> list(@RequestParam String module) {
        return R.ok(dailyGoalService.list(module));
    }

    @PostMapping
    public R<DailyGoal> save(@RequestBody DailyGoal goal) {
        return R.ok(dailyGoalService.save(goal));
    }

    /** 删除某模块某 scope 的目标（含里程碑历史），用于删除自定义目标 */
    @DeleteMapping
    public R<Integer> delete(@RequestParam String module, @RequestParam String scope) {
        return R.ok(dailyGoalService.deleteScope(module, scope));
    }
}

package com.lifedesk.health.controller;

import com.lifedesk.common.R;
import com.lifedesk.health.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 个人健康 Controller。
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        return R.ok(healthService.getStat());
    }

    @GetMapping("/workouts")
    public R<List<com.lifedesk.health.entity.WorkoutRecord>> workouts() {
        return R.ok(healthService.listWorkouts());
    }

    @GetMapping("/diet")
    public R<List<com.lifedesk.health.entity.DietCheck>> diet() {
        return R.ok(healthService.listDiet());
    }

    @GetMapping("/calorie")
    public R<List<com.lifedesk.health.entity.CalorieRecord>> calorie() {
        return R.ok(healthService.listCalorie());
    }

    @GetMapping("/weight")
    public R<List<com.lifedesk.health.entity.WeightRecord>> weight() {
        return R.ok(healthService.listWeight());
    }

    @GetMapping("/exercise")
    public R<List<com.lifedesk.health.entity.ExercisePlan>> exercise() {
        return R.ok(healthService.listExercise());
    }

    @GetMapping("/steps")
    public R<List<com.lifedesk.health.entity.StepRecord>> steps() {
        return R.ok(healthService.listSteps());
    }

    @GetMapping("/vital")
    public R<com.lifedesk.health.entity.VitalSign> vital() {
        return R.ok(healthService.getVitalToday());
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(healthService.overview());
    }

    /** 统一录入接口（首页快速打卡 + 个人健康页记录健康共用） */
    @PostMapping("/record")
    public R<Map<String, Object>> record(@RequestBody Map<String, Object> body) {
        return R.ok(healthService.record(body));
    }
}
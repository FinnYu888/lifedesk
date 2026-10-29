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
 * <p>覆盖 6 大健康组件只读接口 + 录入闭环：
 * <ul>
 *   <li>字典 GET /exercise-dict /food-dict</li>
 *   <li>录入 POST /workout /diet（自动计算消耗/摄入）</li>
 *   <li>汇总 GET /today-summary /trend</li>
 *   <li>删除 DELETE /workout/{id} /diet/{id}</li>
 *   <li>总览 GET /overview（含 today_summary + trend + 字典）</li>
 * </ul>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    // ===== 只读 =====
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

    /** 身体指标：最近一次 + 近 90 天历史 */
    @GetMapping("/body-metrics")
    public R<Map<String, Object>> bodyMetrics() {
        Map<String, Object> d = new java.util.HashMap<>();
        d.put("latest", healthService.getBodyMetricLatest());
        d.put("history", healthService.listBodyMetricHistory());
        return R.ok(d);
    }

    /** 保存身体指标（身高/体重/三围/体脂，按今天覆盖更新） */
    @PostMapping("/body-metric")
    public R<com.lifedesk.health.entity.BodyMetric> saveBodyMetric(@RequestBody Map<String, Object> body) {
        return R.ok(healthService.saveBodyMetric(body));
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

    // ===== 字典 =====
    @GetMapping("/exercise-dict")
    public R<List<com.lifedesk.health.entity.ExerciseDict>> exerciseDict() {
        return R.ok(healthService.listExerciseDict());
    }

    @GetMapping("/food-dict")
    public R<List<com.lifedesk.health.entity.FoodDict>> foodDict(
            @RequestParam(value = "category", required = false) String category) {
        return R.ok(healthService.listFoodDict(category));
    }

    // ===== 录入闭环 =====
    @PostMapping("/workout")
    public R<Map<String, Object>> addWorkout(@RequestBody Map<String, Object> body) {
        return R.ok(healthService.addWorkout(body));
    }

    @PostMapping("/diet")
    public R<Map<String, Object>> addDiet(@RequestBody Map<String, Object> body) {
        return R.ok(healthService.addDiet(body));
    }

    @DeleteMapping("/workout/{id}")
    public R<Void> deleteWorkout(@PathVariable("id") Long id) {
        healthService.deleteWorkout(id);
        return R.ok();
    }

    @DeleteMapping("/diet/{id}")
    public R<Void> deleteDiet(@PathVariable("id") Long id) {
        healthService.deleteDiet(id);
        return R.ok();
    }

    // ===== 汇总 =====
    @GetMapping("/today-summary")
    public R<Map<String, Object>> todaySummary() {
        return R.ok(healthService.todaySummary());
    }

    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend() {
        return R.ok(healthService.trend7d());
    }
}

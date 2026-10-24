package com.lifedesk.health.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.health.entity.*;
import com.lifedesk.health.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人健康 Service。
 *
 * <p>对应 08_个人健康.md：6 大健康组件卡（健身/饮食/卡路里/体重/运动/体征）。</p>
 *
 * @author LifeDesk
 */
@Service
@RequiredArgsConstructor
public class HealthService {

    private final WorkoutRecordMapper workoutRecordMapper;
    private final DietCheckMapper dietCheckMapper;
    private final CalorieRecordMapper calorieRecordMapper;
    private final WeightRecordMapper weightRecordMapper;
    private final ExercisePlanMapper exercisePlanMapper;
    private final StepRecordMapper stepRecordMapper;
    private final VitalSignMapper vitalSignMapper;

    /** 统计卡：周完成率 82% / 今日步数 8,420 / 体重 65.2 */
    public Map<String, Object> getStat() {
        Map<String, Object> stat = new HashMap<>();
        stat.put("week_rate", 82);
        stat.put("week_rate_delta", "+6%");
        stat.put("steps_today", 8420);
        stat.put("steps_percent", 84);
        stat.put("weight", 65.2);
        stat.put("weight_delta", -0.6);
        stat.put("weight_target", 65.0);
        return stat;
    }

    /** 健身动作·本周完成 */
    public List<WorkoutRecord> listWorkouts() {
        return workoutRecordMapper.selectList(
                new LambdaQueryWrapper<WorkoutRecord>()
                        .ge(WorkoutRecord::getRecordDate, LocalDate.now().minusDays(7))
                        .orderByDesc(WorkoutRecord::getRecordDate));
    }

    /** 饮食控制·每周打卡 */
    public List<DietCheck> listDiet() {
        return dietCheckMapper.selectList(
                new LambdaQueryWrapper<DietCheck>()
                        .ge(DietCheck::getCheckDate, LocalDate.now().minusDays(7))
                        .orderByAsc(DietCheck::getCheckDate));
    }

    /** 卡路里·摄入/消耗 */
    public List<CalorieRecord> listCalorie() {
        return calorieRecordMapper.selectList(
                new LambdaQueryWrapper<CalorieRecord>()
                        .ge(CalorieRecord::getRecordDate, LocalDate.now().minusDays(7))
                        .orderByAsc(CalorieRecord::getRecordDate));
    }

    /** 体重·体脂变化趋势 */
    public List<WeightRecord> listWeight() {
        return weightRecordMapper.selectList(
                new LambdaQueryWrapper<WeightRecord>()
                        .ge(WeightRecord::getRecordDate, LocalDate.now().minusDays(30))
                        .orderByAsc(WeightRecord::getRecordDate));
    }

    /** 运动计划达成率 */
    public List<ExercisePlan> listExercise() {
        return exercisePlanMapper.selectList(null);
    }

    /** 步数近 7 天 */
    public List<StepRecord> listSteps() {
        return stepRecordMapper.selectList(
                new LambdaQueryWrapper<StepRecord>()
                        .ge(StepRecord::getRecordDate, LocalDate.now().minusDays(7))
                        .orderByAsc(StepRecord::getRecordDate));
    }

    /** 心率/睡眠/水分 */
    public VitalSign getVitalToday() {
        return vitalSignMapper.selectOne(
                new LambdaQueryWrapper<VitalSign>()
                        .eq(VitalSign::getRecordDate, LocalDate.now()));
    }

    /** 6 大健康组件总览（首页 + 个人健康页通用） */
    public Map<String, Object> overview() {
        Map<String, Object> d = new HashMap<>();
        d.put("stat", getStat());
        d.put("workouts", listWorkouts());
        d.put("diet", listDiet());
        d.put("calorie", listCalorie());
        d.put("weight", listWeight());
        d.put("exercise", listExercise());
        d.put("steps", listSteps());
        d.put("vital", getVitalToday());
        return d;
    }

    /** 统一录入接口，根据 recordType 路由 */
    public Map<String, Object> record(Map<String, Object> body) {
        String type = (String) body.get("recordType");
        if (type == null) {
            throw new BizException("recordType 不能为空");
        }
        switch (type) {
            case "weight":
                return recordWeight(body);
            case "diet":
                return recordDiet(body);
            case "workout":
                return recordWorkout(body);
            case "vital":
                return recordVital(body);
            default:
                throw new BizException("不支持的记录类型: " + type);
        }
    }

    private Map<String, Object> recordWeight(Map<String, Object> body) {
        WeightRecord r = new WeightRecord();
        r.setRecordDate(LocalDate.now());
        Object w = body.get("weight");
        Object f = body.get("fatRate");
        if (w instanceof Number) {
            r.setWeight(((Number) w).doubleValue());
        }
        if (f instanceof Number) {
            r.setFatRate(((Number) f).doubleValue());
        }
        weightRecordMapper.insert(r);
        Map<String, Object> result = new HashMap<>();
        result.put("id", r.getId());
        return result;
    }

    private Map<String, Object> recordDiet(Map<String, Object> body) {
        LocalDate date = LocalDate.now();
        DietCheck exist = dietCheckMapper.selectOne(
                new LambdaQueryWrapper<DietCheck>().eq(DietCheck::getCheckDate, date));
        if (exist == null) {
            exist = new DietCheck();
            exist.setCheckDate(date);
            exist.setBreakfast(0);
            exist.setLunch(0);
            exist.setDinner(0);
            exist.setSnack(0);
            dietCheckMapper.insert(exist);
        }
        if (Boolean.TRUE.equals(body.get("breakfast"))) {
            exist.setBreakfast(1);
        }
        if (Boolean.TRUE.equals(body.get("lunch"))) {
            exist.setLunch(1);
        }
        if (Boolean.TRUE.equals(body.get("dinner"))) {
            exist.setDinner(1);
        }
        if (Boolean.TRUE.equals(body.get("snack"))) {
            exist.setSnack(1);
        }
        dietCheckMapper.updateById(exist);
        Map<String, Object> result = new HashMap<>();
        result.put("id", exist.getId());
        return result;
    }

    private Map<String, Object> recordWorkout(Map<String, Object> body) {
        WorkoutRecord r = new WorkoutRecord();
        r.setPart((String) body.get("part"));
        Object plan = body.get("planCount");
        Object done = body.get("doneCount");
        if (plan instanceof Number) {
            r.setPlanCount(((Number) plan).intValue());
        }
        if (done instanceof Number) {
            r.setDoneCount(((Number) done).intValue());
        }
        r.setStatus("done");
        r.setRecordDate(LocalDate.now());
        workoutRecordMapper.insert(r);
        Map<String, Object> result = new HashMap<>();
        result.put("id", r.getId());
        return result;
    }

    private Map<String, Object> recordVital(Map<String, Object> body) {
        VitalSign v = vitalSignMapper.selectOne(
                new LambdaQueryWrapper<VitalSign>().eq(VitalSign::getRecordDate, LocalDate.now()));
        if (v == null) {
            v = new VitalSign();
            v.setRecordDate(LocalDate.now());
            v.setWaterGoalMl(2500);
            vitalSignMapper.insert(v);
        }
        Object hr = body.get("heartRate");
        Object sl = body.get("sleepHours");
        Object wm = body.get("waterMl");
        if (hr instanceof Number) {
            v.setHeartRate(((Number) hr).intValue());
        }
        if (sl instanceof Number) {
            v.setSleepHours(((Number) sl).doubleValue());
        }
        if (wm instanceof Number) {
            v.setWaterMl(((Number) wm).intValue());
        }
        vitalSignMapper.updateById(v);
        Map<String, Object> result = new HashMap<>();
        result.put("id", v.getId());
        return result;
    }
}
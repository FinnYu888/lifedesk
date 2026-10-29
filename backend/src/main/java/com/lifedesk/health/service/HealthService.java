package com.lifedesk.health.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.health.entity.*;
import com.lifedesk.health.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个人健康 Service。
 *
 * <p>对应 08_个人健康.md：6 大健康组件卡（健身/饮食/卡路里/体重/运动/体征）。
 * 此版本在原只读六组件基础上，新增「可填写 + 自动计算」闭环：
 * <ul>
 *   <li>listExerciseDict / listFoodDict — 提供动作与食物字典供前端选择</li>
 *   <li>addWorkout — 提交运动记录，按字典 MET 或 kcal_per_set 自动算消耗</li>
 *   <li>addDiet   — 提交饮食记录，按食物 kcal_per_100g × amount_g / 100 自动算摄入</li>
 *   <li>todaySummary / trend7d — 汇总今日卡路里平衡 + 7 天趋势，反哺健康页</li>
 * </ul>
 * 写入时同步累加到 calorie_record 当日行，保证 overview 中卡路里图表数据一致。
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
    private final ExerciseDictMapper exerciseDictMapper;
    private final FoodDictMapper foodDictMapper;
    private final DietRecordMapper dietRecordMapper;
    private final BodyMetricMapper bodyMetricMapper;

    /** 默认体重 kg，无体重记录时用于 MET 卡路里估算 */
    private static final double DEFAULT_WEIGHT_KG = 65.0;
    /** 用户目标体重，预留 */
    private static final double WEIGHT_TARGET = 65.0;

    // ============================================================
    // 字典
    // ============================================================

    /** 全部运动动作字典。 */
    public List<ExerciseDict> listExerciseDict() {
        return exerciseDictMapper.selectList(
                new LambdaQueryWrapper<ExerciseDict>()
                        .orderByAsc(ExerciseDict::getPart)
                        .orderByAsc(ExerciseDict::getId));
    }

    /** 食物字典，可选 category 过滤。 */
    public List<FoodDict> listFoodDict(String category) {
        LambdaQueryWrapper<FoodDict> w = new LambdaQueryWrapper<FoodDict>()
                .orderByAsc(FoodDict::getCategory)
                .orderByAsc(FoodDict::getId);
        if (category != null && !category.isEmpty()) {
            w.eq(FoodDict::getCategory, category);
        }
        return foodDictMapper.selectList(w);
    }

    // ============================================================
    // 原有六组件只读接口（保留）
    // ============================================================

    /** 统计卡：周完成率 / 今日步数（step_record 当日真实值） / 体重（最近记录） */
    public Map<String, Object> getStat() {
        Map<String, Object> stat = new HashMap<>();
        // 周完成率：过去 7 天中"有健康打卡"（饮食打卡/运动记录/步数/体征任一）的天数占比，全部来自真实录入
        LocalDate weekStart = LocalDate.now().minusDays(6);
        Set<LocalDate> checkedDays = new HashSet<>();
        for (DietCheck dc : dietCheckMapper.selectList(new LambdaQueryWrapper<DietCheck>()
                .ge(DietCheck::getCheckDate, weekStart))) {
            if (nz(dc.getBreakfast()) + nz(dc.getLunch()) + nz(dc.getDinner()) + nz(dc.getSnack()) > 0) {
                checkedDays.add(dc.getCheckDate());
            }
        }
        for (WorkoutRecord w : workoutRecordMapper.selectList(new LambdaQueryWrapper<WorkoutRecord>()
                .ge(WorkoutRecord::getRecordDate, weekStart))) {
            checkedDays.add(w.getRecordDate());
        }
        for (StepRecord s : stepRecordMapper.selectList(new LambdaQueryWrapper<StepRecord>()
                .ge(StepRecord::getRecordDate, weekStart))) {
            if (s.getSteps() != null && s.getSteps() > 0) {
                checkedDays.add(s.getRecordDate());
            }
        }
        for (VitalSign v : vitalSignMapper.selectList(new LambdaQueryWrapper<VitalSign>()
                .ge(VitalSign::getRecordDate, weekStart))) {
            if ((v.getSleepHours() != null && v.getSleepHours() > 0)
                    || (v.getWaterMl() != null && v.getWaterMl() > 0)) {
                checkedDays.add(v.getRecordDate());
            }
        }
        int weekRate = (int) Math.round(checkedDays.size() * 100.0 / 7);
        stat.put("week_rate", Math.min(100, weekRate));
        // 周完成率环比：本周打卡天数 vs 上一个 7 天的打卡天数
        LocalDate prevStart = weekStart.minusDays(7);
        Set<LocalDate> prevDays = new HashSet<>();
        for (DietCheck dc : dietCheckMapper.selectList(new LambdaQueryWrapper<DietCheck>()
                .ge(DietCheck::getCheckDate, prevStart).le(DietCheck::getCheckDate, weekStart.minusDays(1)))) {
            if (nz(dc.getBreakfast()) + nz(dc.getLunch()) + nz(dc.getDinner()) + nz(dc.getSnack()) > 0) {
                prevDays.add(dc.getCheckDate());
            }
        }
        for (WorkoutRecord w : workoutRecordMapper.selectList(new LambdaQueryWrapper<WorkoutRecord>()
                .ge(WorkoutRecord::getRecordDate, prevStart).le(WorkoutRecord::getRecordDate, weekStart.minusDays(1)))) {
            prevDays.add(w.getRecordDate());
        }
        int prevRate = (int) Math.round(prevDays.size() * 100.0 / 7);
        int delta = Math.min(100, weekRate) - prevRate;
        stat.put("week_rate_delta", (delta >= 0 ? "+" : "") + delta + "%");
        // 今日步数：读取 step_record 当日记录，无记录则为 0（配合每日步数目标计算完成百分比）
        StepRecord todayStep = stepRecordMapper.selectOne(
                new LambdaQueryWrapper<StepRecord>().eq(StepRecord::getRecordDate, LocalDate.now()));
        int stepsToday = todayStep != null && todayStep.getSteps() != null ? todayStep.getSteps() : 0;
        stat.put("steps_today", stepsToday);
        // 体重：最近一条记录；无记录用默认值
        List<WeightRecord> weights = weightRecordMapper.selectList(
                new LambdaQueryWrapper<WeightRecord>()
                        .isNotNull(WeightRecord::getWeight)
                        .orderByDesc(WeightRecord::getRecordDate)
                        .last("LIMIT 2"));
        double weight = !weights.isEmpty() && weights.get(0).getWeight() != null ? weights.get(0).getWeight() : 65.2;
        stat.put("weight", weight);
        // 体重变化：最近两次真实记录差值（无历史记录为 0）
        double weightDelta = 0;
        if (weights.size() >= 2 && weights.get(1).getWeight() != null) {
            weightDelta = Math.round((weight - weights.get(1).getWeight()) * 10) / 10.0;
        }
        stat.put("weight_delta", weightDelta);
        stat.put("weight_target", WEIGHT_TARGET);
        return stat;
    }

    /** null 安全取 int（用于饮食打卡 0/1 标记求和） */
    private int nz(Integer v) {
        return v == null ? 0 : v;
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

    // ============================================================
    // 身体指标（身高/体重/三围/体脂）
    // ============================================================

    /** 最近一次身体指标（无记录返回 null） */
    public BodyMetric getBodyMetricLatest() {
        List<BodyMetric> list = bodyMetricMapper.selectList(
                new LambdaQueryWrapper<BodyMetric>()
                        .orderByDesc(BodyMetric::getRecordDate)
                        .orderByDesc(BodyMetric::getId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 近 90 天身体指标历史（按日期升序，供趋势图/对比） */
    public List<BodyMetric> listBodyMetricHistory() {
        return bodyMetricMapper.selectList(
                new LambdaQueryWrapper<BodyMetric>()
                        .ge(BodyMetric::getRecordDate, LocalDate.now().minusDays(90))
                        .orderByAsc(BodyMetric::getRecordDate));
    }

    /**
     * 保存身体指标：按今天 upsert（同日重复保存为覆盖更新，仅覆盖填写的字段）。
     * 体重/体脂同步镜像到 weight_record（同日 upsert），保持体重趋势卡与统计卡一致。
     */
    public BodyMetric saveBodyMetric(Map<String, Object> body) {
        LocalDate date = LocalDate.now();
        BodyMetric m = bodyMetricMapper.selectOne(
                new LambdaQueryWrapper<BodyMetric>().eq(BodyMetric::getRecordDate, date));
        boolean create = m == null;
        if (create) {
            m = new BodyMetric();
            m.setRecordDate(date);
        }
        Double weight = null;
        Double fat = null;
        Double v;
        if ((v = readPositive(body.get("heightCm"))) != null) {
            m.setHeightCm(v);
        }
        if ((v = readPositive(body.get("weightKg"))) != null) {
            m.setWeightKg(v);
            weight = v;
        }
        if ((v = readPositive(body.get("bustCm"))) != null) {
            m.setBustCm(v);
        }
        if ((v = readPositive(body.get("waistCm"))) != null) {
            m.setWaistCm(v);
        }
        if ((v = readPositive(body.get("hipCm"))) != null) {
            m.setHipCm(v);
        }
        if ((v = readPositive(body.get("bodyFat"))) != null) {
            m.setBodyFat(v);
            fat = v;
        }
        if (create) {
            bodyMetricMapper.insert(m);
        } else {
            bodyMetricMapper.updateById(m);
        }
        // 体重/体脂镜像 weight_record（同日 upsert），统计卡与体重体脂趋势直接复用
        if (weight != null || fat != null) {
            WeightRecord wr = weightRecordMapper.selectOne(
                    new LambdaQueryWrapper<WeightRecord>().eq(WeightRecord::getRecordDate, date));
            if (wr == null) {
                wr = new WeightRecord();
                wr.setRecordDate(date);
                wr.setWeight(weight);
                wr.setFatRate(fat);
                weightRecordMapper.insert(wr);
            } else {
                if (weight != null) {
                    wr.setWeight(weight);
                }
                if (fat != null) {
                    wr.setFatRate(fat);
                }
                weightRecordMapper.updateById(wr);
            }
        }
        return m;
    }

    /** 读取正数（null / 非法 / <=0 一律返回 null 表示未填写） */
    private Double readPositive(Object o) {
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            return d > 0 ? d : null;
        }
        return null;
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
        // 追加今日汇总与 7 天趋势，让前端健康页直接用
        d.put("today_summary", todaySummary());
        d.put("trend", trend7d());
        d.put("exercise_dict", listExerciseDict());
        d.put("food_dict", listFoodDict(null));
        d.put("body_metric", getBodyMetricLatest());
        return d;
    }

    /** 统一录入接口（兼容首页快速打卡 + 个人健康页记录健康）。 */
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
            case "steps":
                return recordSteps(body);
            default:
                throw new BizException("不支持的记录类型: " + type);
        }
    }

    /**
     * 录入今日步数（覆盖式更新，同日重复录入为覆盖）。
     *
     * <p>步数消耗自动折算：kcal = steps × 体重(kg) × 0.0005（70kg 万步 ≈ 350 kcal，步行平均估算），
     * 体重取最近一次体重记录（无则用默认 65kg）。</p>
     *
     * <p>同步 calorie_record 当日 burn：因为步数是覆盖式更新，
     * burn 增量 = 新步数消耗 − 旧步数消耗（旧值存在 step_record.calories），保证消耗不重复累加。</p>
     */
    private Map<String, Object> recordSteps(Map<String, Object> body) {
        Object s = body.get("steps");
        if (!(s instanceof Number) || ((Number) s).intValue() < 0) {
            throw new BizException("steps 必须为非负数字");
        }
        int steps = ((Number) s).intValue();
        LocalDate date = LocalDate.now();
        StepRecord old = stepRecordMapper.selectOne(
                new LambdaQueryWrapper<StepRecord>().eq(StepRecord::getRecordDate, date));
        double oldCalories = old != null && old.getCalories() != null ? old.getCalories() : 0;

        // 步数消耗折算：steps × 体重(kg) × 0.0005
        double weight = latestWeightOr(DEFAULT_WEIGHT_KG);
        double calories = steps * weight * 0.0005;

        StepRecord r;
        if (old == null) {
            r = new StepRecord();
            r.setRecordDate(date);
            r.setSteps(steps);
            r.setCalories(round1(calories));
            stepRecordMapper.insert(r);
        } else {
            r = old;
            r.setSteps(steps);
            r.setCalories(round1(calories));
            stepRecordMapper.updateById(r);
        }
        // 覆盖式同步当日消耗（增量 = 新步数消耗 − 旧步数消耗）
        syncCalorie(date, 0, round1(calories) - oldCalories);

        Map<String, Object> result = new HashMap<>();
        result.put("id", r.getId());
        result.put("steps", steps);
        result.put("caloriesBurned", round1(calories));
        return result;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> recordWorkout(Map<String, Object> body) {
        // 兼容旧接口：body 直接当 WorkoutRecord 写入
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

    // ============================================================
    // 录入闭环：addWorkout / addDiet + 同步 calorie_record
    // ============================================================

    /**
     * 提交一条运动记录。
     *
     * <p>必填：exerciseId 或 exerciseName。
     * 可选：sets / reps / weightKg / durationMin。
     * 后端按以下优先级计算 calories_burned：
     * <ol>
     *   <li>durationMin > 0：按 MET × 体重(kg) × 时长(h)</li>
     *   <li>sets > 0：按字典 kcal_per_set × sets</li>
     *   <li>兜底：50 kcal</li>
     * </ol>
     * 写完后同步累加到 calorie_record 当日 burn。
     */
    public Map<String, Object> addWorkout(Map<String, Object> body) {
        Long exerciseId = toLong(body.get("exerciseId"));
        String exerciseName = (String) body.get("exerciseName");
        ExerciseDict dict = null;
        if (exerciseId != null) {
            dict = exerciseDictMapper.selectById(exerciseId);
        }
        if (dict == null && exerciseName != null && !exerciseName.isEmpty()) {
            dict = exerciseDictMapper.selectOne(
                    new LambdaQueryWrapper<ExerciseDict>().eq(ExerciseDict::getName, exerciseName).last("LIMIT 1"));
        }
        if (dict == null) {
            throw new BizException("运动不存在，请选择字典内的动作或先扩展字典");
        }
        int sets = toInt(body.get("sets"), 0);
        int reps = toInt(body.get("reps"), 0);
        double weightKg = toDouble(body.get("weightKg"), 0);
        int durationMin = toInt(body.get("durationMin"), 0);

        double burned = calcBurned(dict, sets, durationMin, weightKg);

        WorkoutRecord r = new WorkoutRecord();
        r.setExerciseId(dict.getId());
        r.setExerciseName(dict.getName());
        r.setPart(dict.getPart());
        r.setSets(sets);
        r.setReps(reps);
        r.setWeightKg(weightKg);
        r.setDurationMin(durationMin);
        r.setCaloriesBurned(round1(burned));
        r.setPlanCount(sets);
        r.setDoneCount(sets);
        r.setStatus("done");
        r.setRecordDate(LocalDate.now());
        workoutRecordMapper.insert(r);

        syncCalorie(LocalDate.now(), 0, burned);

        Map<String, Object> result = new HashMap<>();
        result.put("id", r.getId());
        result.put("exerciseName", r.getExerciseName());
        result.put("part", r.getPart());
        result.put("sets", r.getSets());
        result.put("reps", r.getReps());
        result.put("weightKg", r.getWeightKg());
        result.put("durationMin", r.getDurationMin());
        result.put("caloriesBurned", r.getCaloriesBurned());
        return result;
    }

    /**
     * 提交一条饮食记录。
     *
     * <p>必填：foodId 或 foodName；meal（breakfast/lunch/dinner/snack）；amountG。
     * calories_intake = food.kcal_per_100g × amount_g / 100。
     * 写完后同步累加到 calorie_record 当日 intake。
     */
    public Map<String, Object> addDiet(Map<String, Object> body) {
        Long foodId = toLong(body.get("foodId"));
        String foodName = (String) body.get("foodName");
        FoodDict dict = null;
        if (foodId != null) {
            dict = foodDictMapper.selectById(foodId);
        }
        if (dict == null && foodName != null && !foodName.isEmpty()) {
            dict = foodDictMapper.selectOne(
                    new LambdaQueryWrapper<FoodDict>().eq(FoodDict::getName, foodName).last("LIMIT 1"));
        }
        if (dict == null) {
            throw new BizException("食物不存在，请选择字典内的食物或先扩展字典");
        }
        String meal = (String) body.get("meal");
        if (meal == null || meal.isEmpty()) {
            throw new BizException("meal 必填，可选 breakfast/lunch/dinner/snack");
        }
        double amountG = toDouble(body.get("amountG"), dict.getDefaultAmount() == null ? 100d : dict.getDefaultAmount().doubleValue());
        if (amountG <= 0) {
            throw new BizException("amountG 必须大于 0");
        }
        double intake = dict.getKcalPer100g() == null ? 0 : dict.getKcalPer100g() * amountG / 100.0;

        DietRecord r = new DietRecord();
        r.setFoodId(dict.getId());
        r.setFoodName(dict.getName());
        r.setMeal(meal);
        r.setAmountG(round1(amountG));
        r.setCaloriesIntake(round1(intake));
        r.setRecordDate(LocalDate.now());
        dietRecordMapper.insert(r);

        syncCalorie(LocalDate.now(), intake, 0);

        Map<String, Object> result = new HashMap<>();
        result.put("id", r.getId());
        result.put("foodName", r.getFoodName());
        result.put("meal", r.getMeal());
        result.put("amountG", r.getAmountG());
        result.put("caloriesIntake", r.getCaloriesIntake());
        return result;
    }

    /** 删除运动记录（同时从当日 calorie_record 扣回 burn）。 */
    public void deleteWorkout(Long id) {
        WorkoutRecord r = workoutRecordMapper.selectById(id);
        if (r == null) {
            throw new BizException("记录不存在");
        }
        workoutRecordMapper.deleteById(id);
        if (r.getRecordDate() != null && r.getCaloriesBurned() != null) {
            syncCalorie(r.getRecordDate(), 0, -r.getCaloriesBurned());
        }
    }

    /** 删除饮食记录（同时从当日 calorie_record 扣回 intake）。 */
    public void deleteDiet(Long id) {
        DietRecord r = dietRecordMapper.selectById(id);
        if (r == null) {
            throw new BizException("记录不存在");
        }
        dietRecordMapper.deleteById(id);
        if (r.getRecordDate() != null && r.getCaloriesIntake() != null) {
            syncCalorie(r.getRecordDate(), -r.getCaloriesIntake(), 0);
        }
    }

    // ============================================================
    // 今日汇总 + 7 天趋势
    // ============================================================

    /**
     * 今日卡路里平衡汇总。
     * <ul>
     *   <li>intake_today  今日总摄入（来自 diet_record 当日 sum(calories_intake)）</li>
     *   <li>burn_today    今日总消耗（来自 workout_record 当日 sum(calories_burned)）</li>
     *   <li>net           净值 = intake - burn；>0 红字表示摄入超标</li>
     *   <li>workouts_today 今日运动记录列表</li>
     *   <li>diet_today    今日饮食记录按餐次分组</li>
     *   <li>target_intake 建议摄入（1800）</li>
     *   <li>target_burn   建议消耗（2200，含基础代谢）</li>
     * </ul>
     */
    public Map<String, Object> todaySummary() {
        LocalDate today = LocalDate.now();
        List<WorkoutRecord> workouts = workoutRecordMapper.selectList(
                new LambdaQueryWrapper<WorkoutRecord>()
                        .eq(WorkoutRecord::getRecordDate, today)
                        .orderByDesc(WorkoutRecord::getId));
        List<DietRecord> diets = dietRecordMapper.selectList(
                new LambdaQueryWrapper<DietRecord>()
                        .eq(DietRecord::getRecordDate, today)
                        .orderByAsc(DietRecord::getId));

        double burnToday = workouts.stream()
                .filter(w -> w.getCaloriesBurned() != null)
                .mapToDouble(WorkoutRecord::getCaloriesBurned)
                .sum();
        double intakeToday = diets.stream()
                .filter(d -> d.getCaloriesIntake() != null)
                .mapToDouble(DietRecord::getCaloriesIntake)
                .sum();
        // 步数消耗计入今日总消耗（step_record.calories，录入步数时自动折算）
        StepRecord todayStep = stepRecordMapper.selectOne(
                new LambdaQueryWrapper<StepRecord>().eq(StepRecord::getRecordDate, today));
        int stepsToday = todayStep != null && todayStep.getSteps() != null ? todayStep.getSteps() : 0;
        double stepsBurn = todayStep != null && todayStep.getCalories() != null ? todayStep.getCalories() : 0;
        burnToday += stepsBurn;

        Map<String, Object> dietByMeal = new LinkedHashMap<>();
        dietByMeal.put("breakfast", filterMeal(diets, "breakfast"));
        dietByMeal.put("lunch", filterMeal(diets, "lunch"));
        dietByMeal.put("dinner", filterMeal(diets, "dinner"));
        dietByMeal.put("snack", filterMeal(diets, "snack"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", today.toString());
        data.put("intake_today", Math.round(intakeToday));
        data.put("burn_today", Math.round(burnToday));
        data.put("net", Math.round(intakeToday - burnToday));
        // 步数明细：今日步数 + 步数折算消耗（前端展示"含步数消耗 X kcal"）
        data.put("steps_today", stepsToday);
        data.put("steps_burn", Math.round(stepsBurn));
        data.put("target_intake", 1800);
        data.put("target_burn", 2200);
        data.put("workouts_today", workouts);
        data.put("diet_today", dietByMeal);
        return data;
    }

    /**
     * 7 天卡路里平衡趋势（按日升序）。
     * 每天 = {date, intake, burn, net}，net = intake - burn。
     */
    public List<Map<String, Object>> trend7d() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);

        List<CalorieRecord> records = calorieRecordMapper.selectList(
                new LambdaQueryWrapper<CalorieRecord>()
                        .ge(CalorieRecord::getRecordDate, start)
                        .le(CalorieRecord::getRecordDate, today)
                        .orderByAsc(CalorieRecord::getRecordDate));
        Map<LocalDate, CalorieRecord> byDate = records.stream()
                .collect(Collectors.toMap(CalorieRecord::getRecordDate, c -> c, (a, b) -> a, LinkedHashMap::new));

        // 步数消耗按日合并：每天 burn += step_record.calories（录入步数时自动折算）
        Map<LocalDate, Double> stepsBurnByDate = stepRecordMapper.selectList(
                        new LambdaQueryWrapper<StepRecord>()
                                .ge(StepRecord::getRecordDate, start)
                                .le(StepRecord::getRecordDate, today))
                .stream()
                .filter(s -> s.getCalories() != null)
                .collect(Collectors.toMap(StepRecord::getRecordDate, StepRecord::getCalories, (a, b) -> a));

        // 兜底：用 workout_record / diet_record 实时聚合补全 7 天
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            CalorieRecord c = byDate.get(d);
            int intake = c != null && c.getIntake() != null ? c.getIntake() : 0;
            int burn = c != null && c.getBurn() != null ? c.getBurn() : 0;
            // 优先用 records 表，若 0 再尝试 sum 表（防止先 insert 后同步失败）
            if (intake == 0) {
                intake = sumDietIntake(d);
            }
            if (burn == 0) {
                burn = sumWorkoutBurn(d);
            }
            // 步数消耗计入当日总消耗
            Double stepsBurn = stepsBurnByDate.get(d);
            if (stepsBurn != null) {
                burn += (int) Math.round(stepsBurn);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString().substring(5)); // MM-dd
            row.put("intake", intake);
            row.put("burn", burn);
            row.put("net", intake - burn);
            result.add(row);
        }
        return result;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 按 MET × 体重(kg) × 时长(h) 或 字典 kcal_per_set × sets 计算消耗。 */
    private double calcBurned(ExerciseDict dict, int sets, int durationMin, double weightKg) {
        // 1) 有氧/拉伸：时长模式 MET × weight × h
        if (durationMin > 0 && dict.getMet() != null && dict.getMet() > 0) {
            double w = weightKg > 0 ? weightKg : latestWeightOr(DEFAULT_WEIGHT_KG);
            return dict.getMet() * w * (durationMin / 60.0);
        }
        // 2) 力量：组数模式 kcal_per_set × sets
        if (sets > 0 && dict.getKcalPerSet() != null && dict.getKcalPerSet() > 0) {
            return dict.getKcalPerSet() * sets;
        }
        // 3) 时长但没 MET：每分钟 6 kcal 简单估算
        if (durationMin > 0) {
            return 6.0 * durationMin;
        }
        // 4) 兜底
        return 50.0;
    }

    /** 取最近一条体重记录，没有就用默认值。 */
    private double latestWeightOr(double fallback) {
        WeightRecord latest = weightRecordMapper.selectOne(
                new LambdaQueryWrapper<WeightRecord>()
                        .isNotNull(WeightRecord::getWeight)
                        .orderByDesc(WeightRecord::getRecordDate)
                        .last("LIMIT 1"));
        if (latest != null && latest.getWeight() != null && latest.getWeight() > 0) {
            return latest.getWeight();
        }
        return fallback;
    }

    /** 把新增的 burn/intake 累加到当日 calorie_record（不存在则插入）。 */
    private void syncCalorie(LocalDate date, double intakeDelta, double burnDelta) {
        CalorieRecord c = calorieRecordMapper.selectOne(
                new LambdaQueryWrapper<CalorieRecord>().eq(CalorieRecord::getRecordDate, date));
        if (c == null) {
            c = new CalorieRecord();
            c.setRecordDate(date);
            c.setIntake((int) Math.round(Math.max(0, intakeDelta)));
            c.setBurn((int) Math.round(Math.max(0, burnDelta)));
            calorieRecordMapper.insert(c);
        } else {
            int newIntake = (int) Math.max(0, (c.getIntake() == null ? 0 : c.getIntake()) + intakeDelta);
            int newBurn = (int) Math.max(0, (c.getBurn() == null ? 0 : c.getBurn()) + burnDelta);
            c.setIntake(newIntake);
            c.setBurn(newBurn);
            calorieRecordMapper.updateById(c);
        }
    }

    private List<DietRecord> filterMeal(List<DietRecord> all, String meal) {
        return all.stream().filter(d -> meal.equals(d.getMeal())).collect(Collectors.toList());
    }

    private int sumDietIntake(LocalDate date) {
        List<DietRecord> list = dietRecordMapper.selectList(
                new LambdaQueryWrapper<DietRecord>().eq(DietRecord::getRecordDate, date));
        return (int) Math.round(list.stream()
                .filter(d -> d.getCaloriesIntake() != null)
                .mapToDouble(DietRecord::getCaloriesIntake)
                .sum());
    }

    private int sumWorkoutBurn(LocalDate date) {
        List<WorkoutRecord> list = workoutRecordMapper.selectList(
                new LambdaQueryWrapper<WorkoutRecord>().eq(WorkoutRecord::getRecordDate, date));
        return (int) Math.round(list.stream()
                .filter(w -> w.getCaloriesBurned() != null)
                .mapToDouble(WorkoutRecord::getCaloriesBurned)
                .sum());
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    private int toInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return def; }
    }

    private double toDouble(Object o, double def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return def; }
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}

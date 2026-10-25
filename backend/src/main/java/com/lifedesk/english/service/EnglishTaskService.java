package com.lifedesk.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.english.entity.EnglishTask;
import com.lifedesk.english.mapper.EnglishTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 英语提升 Service。
 *
 * <p>每条任务代表一项英语能力提升计划，按 category 分类。
 * 用户可每日打卡（completeToday），累计完成次数 +1、连续天数 +1（断签则清零）。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnglishTaskService {

    private final EnglishTaskMapper englishTaskMapper;

    /**
     * 列表查询。
     *
     * @param category 可选 vocabulary/grammar/listening/speaking/reading/writing
     * @param keyword  模糊匹配 name/daily_goal
     */
    public List<EnglishTask> list(String category, String keyword) {
        QueryWrapper<EnglishTask> qw = new QueryWrapper<>();
        if (category != null && !category.trim().isEmpty()) {
            qw.eq("category", category.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("name", kw).or().like("daily_goal", kw));
        }
        qw.orderByDesc("update_time");
        return englishTaskMapper.selectList(qw);
    }

    public EnglishTask create(EnglishTask task) {
        if (task.getName() == null || task.getName().trim().isEmpty()) {
            throw new BizException("任务名称不能为空");
        }
        normalizeCategory(task);
        if (task.getTargetDays() == null || task.getTargetDays() <= 0) {
            task.setTargetDays(30);
        }
        if (task.getTotalCompleted() == null) task.setTotalCompleted(0);
        if (task.getStreakDays() == null) task.setStreakDays(0);
        englishTaskMapper.insert(task);
        return task;
    }

    public EnglishTask update(Long id, EnglishTask incoming) {
        EnglishTask existed = englishTaskMapper.selectById(id);
        if (existed == null) {
            throw new BizException("任务不存在或已被删除");
        }
        if (incoming.getName() != null && !incoming.getName().trim().isEmpty()) {
            existed.setName(incoming.getName());
        }
        if (incoming.getCategory() != null && !incoming.getCategory().trim().isEmpty()) {
            existed.setCategory(incoming.getCategory().trim());
        }
        if (incoming.getDailyGoal() != null) {
            existed.setDailyGoal(incoming.getDailyGoal());
        }
        if (incoming.getTargetDays() != null && incoming.getTargetDays() > 0) {
            existed.setTargetDays(incoming.getTargetDays());
        }
        englishTaskMapper.updateById(existed);
        return existed;
    }

    public void delete(Long id) {
        EnglishTask existed = englishTaskMapper.selectById(id);
        if (existed == null) {
            throw new BizException("任务不存在或已被删除");
        }
        englishTaskMapper.deleteById(id);
    }

    /**
     * 今日打卡。
     * <p>同一任务今日已打卡则幂等返回（不重复 +1）；
     * 若距上次打卡超过 1 天（即昨日未打卡），streakDays 清零再加 1；
     * 若距上次打卡正好 1 天（即昨日已打卡），streakDays +1；
     * 首次打卡则 streakDays = 1。</p>
     */
    public EnglishTask completeToday(Long id) {
        EnglishTask existed = englishTaskMapper.selectById(id);
        if (existed == null) {
            throw new BizException("任务不存在或已被删除");
        }
        LocalDate today = LocalDate.now();
        LocalDate last = existed.getLastDoneDate();

        if (last != null && last.isEqual(today)) {
            // 今日已打卡，幂等返回
            return existed;
        }

        // 累计 +1
        int total = (existed.getTotalCompleted() == null ? 0 : existed.getTotalCompleted()) + 1;
        existed.setTotalCompleted(total);

        // streak 计算
        int newStreak;
        if (last == null) {
            newStreak = 1;
        } else {
            long gap = ChronoUnit.DAYS.between(last, today);
            if (gap == 1) {
                // 昨日已打卡，连续
                newStreak = (existed.getStreakDays() == null ? 0 : existed.getStreakDays()) + 1;
            } else {
                // 断签，重新计数
                newStreak = 1;
            }
        }
        existed.setStreakDays(newStreak);
        existed.setLastDoneDate(today);

        englishTaskMapper.updateById(existed);
        log.info("[English] 任务 {} 今日打卡，累计 {} 次，连续 {} 天", existed.getName(), total, newStreak);
        return existed;
    }

    /**
     * 统计：
     * <ul>
     *   <li>total_tasks：任务总数</li>
     *   <li>today_done：今日已打卡任务数</li>
     *   <li>week_completed：本周累计打卡次数（按 lastDoneDate 本周过滤）</li>
     *   <li>avg_completion_rate：平均完成率（totalCompleted/targetDays 取均值）</li>
     *   <li>by_category：按类别分组的任务数</li>
     * </ul>
     */
    public Map<String, Object> getStat() {
        List<EnglishTask> all = englishTaskMapper.selectList(null);
        LocalDate today = LocalDate.now();

        long totalTasks = all.size();
        long todayDone = all.stream()
                .filter(t -> t.getLastDoneDate() != null && t.getLastDoneDate().isEqual(today))
                .count();

        LocalDate weekStart = today.minusDays(6);
        long weekCompleted = all.stream()
                .mapToInt(t -> {
                    if (t.getLastDoneDate() == null) return 0;
                    return (t.getLastDoneDate().isAfter(weekStart.minusDays(1)) || t.getLastDoneDate().isEqual(weekStart))
                            ? 1 : 0;
                }).sum();

        double avgRate = all.stream()
                .mapToDouble(t -> {
                    if (t.getTargetDays() == null || t.getTargetDays() <= 0) return 0;
                    if (t.getTotalCompleted() == null) return 0;
                    return Math.min(100.0, 100.0 * t.getTotalCompleted() / t.getTargetDays());
                })
                .average()
                .orElse(0);

        Map<String, Object> byCategory = new HashMap<>();
        for (EnglishTask t : all) {
            String cat = t.getCategory() == null ? "other" : t.getCategory();
            byCategory.merge(cat, 1, (a, b) -> (int) a + (int) b);
        }

        Map<String, Object> stat = new HashMap<>();
        stat.put("total_tasks", totalTasks);
        stat.put("today_done", todayDone);
        stat.put("week_completed", weekCompleted);
        stat.put("avg_completion_rate", Math.round(avgRate * 10) / 10.0);
        stat.put("by_category", byCategory);
        return stat;
    }

    /** 规范化 category 字段 */
    private void normalizeCategory(EnglishTask task) {
        String c = task.getCategory();
        if (c == null || c.trim().isEmpty()) {
            task.setCategory("other");
        } else {
            task.setCategory(c.trim().toLowerCase());
        }
    }
}

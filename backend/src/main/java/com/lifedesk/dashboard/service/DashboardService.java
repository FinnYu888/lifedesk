package com.lifedesk.dashboard.service;

import com.lifedesk.english.service.EnglishTaskService;
import com.lifedesk.favorite.service.FavoriteService;
import com.lifedesk.health.service.HealthService;
import com.lifedesk.monitor.service.MonitorService;
import com.lifedesk.project.service.ProjectService;
import com.lifedesk.schedule.service.ScheduleService;
import com.lifedesk.skill.service.SkillService;
import com.lifedesk.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 首页 Dashboard Service。
 *
 * <p>对应 02_首页.md：问候区 + 模块状态六宫格 + 个人健康六组件总览。</p>
 *
 * @author LifeDesk
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final ScheduleService scheduleService;
    private final MonitorService monitorService;
    private final SkillService skillService;
    private final HealthService healthService;
    private final FavoriteService favoriteService;
    private final EnglishTaskService englishTaskService;

    /** 问候区 + 今日摘要 */
    public Map<String, Object> greeting() {
        int hour = LocalDateTime.now().getHour();
        String prefix;
        if (hour < 6) {
            prefix = "凌晨好";
        } else if (hour < 12) {
            prefix = "早上好";
        } else if (hour < 14) {
            prefix = "中午好";
        } else if (hour < 18) {
            prefix = "下午好";
        } else if (hour < 22) {
            prefix = "晚上好";
        } else {
            prefix = "夜深了";
        }
        String greeting = prefix + "，林深";

        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA));
        String weekStr = LocalDate.now().getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINA);

        Map<String, Object> data = new HashMap<>();
        data.put("greeting", greeting);
        data.put("date", dateStr);
        data.put("week", weekStr);
        data.put("today_meetings", 3);
        data.put("today_meeting_with_call", 1);
        data.put("summary", dateStr + " " + weekStr + " · 今日 3 场日程 · 1 个会议");
        return data;
    }

    /** 模块状态六宫格 */
    public Map<String, Object> moduleStat() {
        Map<String, Object> stat = new HashMap<>();

        Map<String, Object> project = new HashMap<>();
        project.put("count", projectService.getStat().get("active_projects"));
        project.put("note", "今日 +24 处理");
        stat.put("project", project);

        Map<String, Object> task = new HashMap<>();
        task.put("count", taskService.listByStatus("doing").size());
        task.put("note", "3 项紧急");
        stat.put("task", task);

        Map<String, Object> monitor = new HashMap<>();
        Map<String, Object> ms = monitorService.getStat();
        monitor.put("count", ms.get("online_services"));
        monitor.put("note", ms.get("alerts") + " 项预警");
        stat.put("monitor", monitor);

        Map<String, Object> schedule = new HashMap<>();
        schedule.put("count", scheduleService.getStat().get("today_meetings"));
        schedule.put("note", "下一场 14:00");
        stat.put("schedule", schedule);

        Map<String, Object> skill = new HashMap<>();
        skill.put("count", skillService.getStat().get("total_skills"));
        skill.put("note", "3 项待提升");
        stat.put("skill", skill);

        Map<String, Object> health = new HashMap<>();
        health.put("count", "82%");
        health.put("note", "周达成率 ↑");
        stat.put("health", health);

        Map<String, Object> favorite = new HashMap<>();
        Map<String, Object> favStat = favoriteService.getStat();
        long favTotal = ((Number) favStat.getOrDefault("total", 0)).longValue();
        long linkCount = ((Number) favStat.getOrDefault("links", 0)).longValue();
        long noteCount = ((Number) favStat.getOrDefault("notes", 0)).longValue();
        favorite.put("count", favTotal);
        favorite.put("note", linkCount + " 收藏 · " + noteCount + " 笔记");
        stat.put("favorite", favorite);

        Map<String, Object> english = new HashMap<>();
        Map<String, Object> engStat = englishTaskService.getStat();
        english.put("count", engStat.get("avg_completion_rate") + "%");
        english.put("note", "今日 " + engStat.get("today_done") + "/" + engStat.get("total_tasks") + " 已完成");
        stat.put("english", english);

        return stat;
    }

    /** 首页整体聚合（一次拉全 6 模块六宫格 + 6 健康组件） */
    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("greeting", greeting());
        data.put("module_stat", moduleStat());
        data.put("health", healthService.overview());
        data.put("project", projectService.getStat());
        data.put("task_count", taskService.listByStatus(null).size());
        return data;
    }
}
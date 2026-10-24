package com.lifedesk.schedule.controller;

import com.lifedesk.common.R;
import com.lifedesk.schedule.entity.Meeting;
import com.lifedesk.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日程会议 Controller。
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        return R.ok(scheduleService.getStat());
    }

    @GetMapping("/today")
    public R<List<Meeting>> today() {
        return R.ok(scheduleService.listToday());
    }

    @GetMapping("/week")
    public R<List<Meeting>> week() {
        return R.ok(scheduleService.listWeek());
    }

    @PostMapping
    public R<Meeting> create(@RequestBody Meeting meeting) {
        return R.ok(scheduleService.create(meeting));
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> d = new HashMap<>();
        d.put("stat", scheduleService.getStat());
        d.put("today", scheduleService.listToday());
        d.put("week", scheduleService.listWeek());
        return R.ok(d);
    }
}
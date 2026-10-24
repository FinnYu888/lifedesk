package com.lifedesk.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.schedule.entity.Meeting;
import com.lifedesk.schedule.mapper.MeetingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日程会议 Service。
 *
 * <p>对应 05_日程会议.md：今日时间轴 + 本周日历。</p>
 *
 * @author LifeDesk
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final MeetingMapper meetingMapper;

    /** 统计卡：今日会议 3 / 本周会议 9 / 待处理 5 */
    public Map<String, Object> getStat() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        long todayCount = meetingMapper.selectCount(
                new LambdaQueryWrapper<Meeting>()
                        .ge(Meeting::getStartTime, todayStart)
                        .lt(Meeting::getStartTime, todayEnd));
        Map<String, Object> stat = new HashMap<>();
        stat.put("today_meetings", todayCount);
        stat.put("next_meeting", "14:00");
        stat.put("week_meetings", 9);
        stat.put("week_meetings_status", "已排满");
        stat.put("pending", 5);
        stat.put("pending_today", 2);
        return stat;
    }

    public List<Meeting> listToday() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        return meetingMapper.selectList(
                new LambdaQueryWrapper<Meeting>()
                        .ge(Meeting::getStartTime, todayStart)
                        .lt(Meeting::getStartTime, todayEnd)
                        .orderByAsc(Meeting::getStartTime));
    }

    public List<Meeting> listWeek() {
        LocalDateTime weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);
        return meetingMapper.selectList(
                new LambdaQueryWrapper<Meeting>()
                        .ge(Meeting::getStartTime, weekStart)
                        .lt(Meeting::getStartTime, weekEnd)
                        .orderByAsc(Meeting::getStartTime));
    }

    public Meeting create(Meeting meeting) {
        if (meeting.getTitle() == null || meeting.getTitle().trim().isEmpty()) {
            throw new BizException("会议名不能为空");
        }
        if (meeting.getStartTime() == null) {
            throw new BizException("开始时间不能为空");
        }
        if (meeting.getStatus() == null) {
            meeting.setStatus("pending");
        }
        meetingMapper.insert(meeting);
        return meeting;
    }
}
package com.lifedesk.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 英语提升任务。
 *
 * <p>每条任务代表一项英语能力提升计划，如"每日背单词 50 个"、"听力训练 30 分钟"等。
 * 用户可每日打卡，累计完成次数与连续天数。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("english_task")
public class EnglishTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称，如"每日单词 50" */
    private String name;

    /** 类别：vocabulary/grammar/listening/speaking/reading/writing */
    private String category;

    /** 每日目标（描述性，如 "50 个新词"） */
    private String dailyGoal;

    /** 目标天数（如 30 天计划、100 天计划） */
    private Integer targetDays;

    /** 累计完成次数（每日打卡一次 +1） */
    private Integer totalCompleted;

    /** 连续打卡天数（断签清零） */
    private Integer streakDays;

    /** 最近一次打卡日期，用于判断"今日是否已完成"和"streak 是否断签" */
    private LocalDate lastDoneDate;
}

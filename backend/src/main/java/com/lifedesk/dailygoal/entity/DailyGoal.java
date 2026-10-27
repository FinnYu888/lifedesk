package com.lifedesk.dailygoal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 每日目标（健康/英语通用）。
 *
 * <p>同一 (module, scope) 的多条记录按 start_date 组成里程碑历史：
 * 每次修改目标都会生成一条新记录（start_date=当天），即「进入下一阶段」的分割点。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_goal")
public class DailyGoal extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块：health / english */
    private String module;

    /** 范围标识：health 指标 key（steps/intake/burn/sleep/water）；english 任务 ID */
    private String scope;

    /** 展示名（健康指标名 / 英语任务名） */
    private String label;

    /** 目标值：数字（健康）或描述文本（英语） */
    private String goalValue;

    /** 单位（kcal/h/L/步 等，可选） */
    private String unit;

    /** 生效日期，即里程碑分割点 */
    private LocalDate startDate;
}

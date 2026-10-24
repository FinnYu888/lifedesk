package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 健身动作记录。
 *
 * <p>记录每条运动：动作名 + 部位 + 组数 × 次数 × 重量，或时长；
 * calories_burned 由后端按字典 MET / kcal_per_set 自动算。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workout_record")
public class WorkoutRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 exercise_dict.id */
    private Long exerciseId;

    /** 动作名冗余，便于列表展示 */
    private String exerciseName;

    /** 部位冗余：胸/背/腿/核心/肩臂/有氧 */
    private String part;

    /** 组数 */
    private Integer sets;

    /** 每组次数 */
    private Integer reps;

    /** 负重 kg（有氧/核心可 0） */
    private Double weightKg;

    /** 时长 分钟（有氧/拉伸优先） */
    private Integer durationMin;

    /** 消耗 kcal，后端自动计算 */
    private Double caloriesBurned;

    /** 原计划组数，保留兼容 */
    private Integer planCount;

    /** 实际完成组数 */
    private Integer doneCount;

    /** done / pending */
    private String status;

    private LocalDate recordDate;
}

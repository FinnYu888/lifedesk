package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 每日步数。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("step_record")
public class StepRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate recordDate;

    private Integer steps;

    private Integer goal;

    /** 步数折算消耗 kcal = steps × 体重(kg) × 0.0005（录入步数时后端自动计算并同步 calorie_record） */
    private Double calories;
}
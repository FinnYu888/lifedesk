package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运动计划达成率。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exercise_plan")
public class ExercisePlan extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;

    private Integer rate;

    private Integer reached;
}
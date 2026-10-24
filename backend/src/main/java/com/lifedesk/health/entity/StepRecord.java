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
}
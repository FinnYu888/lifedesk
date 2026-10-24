package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 生理体征：心率/睡眠/水分。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vital_sign")
public class VitalSign extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate recordDate;

    private Integer heartRate;

    private Double sleepHours;

    private Integer waterMl;

    private Integer waterGoalMl;
}
package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 身体指标：身高 / 体重 / 三围（胸围、腰围、臀围）/ 体脂率。
 *
 * <p>按天一条（record_date 唯一），保存即覆盖当日值；体重/体脂同步镜像到
 * {@code weight_record}，保持体重趋势卡与统计卡数据一致。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("body_metric")
public class BodyMetric extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 记录日期（按天一条，同日重复保存为覆盖更新） */
    private LocalDate recordDate;

    /** 身高 cm */
    private Double heightCm;

    /** 体重 kg */
    private Double weightKg;

    /** 胸围 cm */
    private Double bustCm;

    /** 腰围 cm */
    private Double waistCm;

    /** 臀围 cm */
    private Double hipCm;

    /** 体脂率 % */
    private Double bodyFat;
}

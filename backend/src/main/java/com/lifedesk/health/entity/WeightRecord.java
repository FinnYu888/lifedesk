package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 体重体脂记录。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("weight_record")
public class WeightRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate recordDate;

    private Double weight;

    private Double fatRate;
}
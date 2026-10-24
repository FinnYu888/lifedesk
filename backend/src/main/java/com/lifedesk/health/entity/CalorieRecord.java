package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 卡路里每日记录。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("calorie_record")
public class CalorieRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate recordDate;

    /** 摄入 kcal */
    private Integer intake;

    /** 消耗 kcal */
    private Integer burn;
}
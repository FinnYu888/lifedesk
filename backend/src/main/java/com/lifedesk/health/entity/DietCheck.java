package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 饮食打卡。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("diet_check")
public class DietCheck extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate checkDate;

    private Integer breakfast;

    private Integer lunch;

    private Integer dinner;

    private Integer snack;
}
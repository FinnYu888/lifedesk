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
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workout_record")
public class WorkoutRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部位 胸/背/腿 */
    private String part;

    private Integer planCount;

    private Integer doneCount;

    /** done / pending */
    private String status;

    private LocalDate recordDate;
}
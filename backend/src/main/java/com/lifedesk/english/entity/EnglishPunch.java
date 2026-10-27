package com.lifedesk.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 英语任务每日打卡记录（看板按日统计用）。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("english_punch")
public class EnglishPunch extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务 ID */
    private Long taskId;

    /** 打卡日期 */
    private LocalDate doneDate;
}

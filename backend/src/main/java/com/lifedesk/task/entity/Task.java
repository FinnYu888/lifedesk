package com.lifedesk.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务工单。
 *
 * <p>对应 06_任务工单.md 三列看板。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class Task extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** todo / doing / done */
    private String status;

    /** normal / urgent */
    private String priority;

    /** 今日截止/本周内/昨天... */
    private String dueLabel;

    /** assigned / unassigned */
    private String assignState;

    private Integer sortNo;
}
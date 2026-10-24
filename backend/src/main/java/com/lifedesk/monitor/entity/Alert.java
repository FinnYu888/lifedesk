package com.lifedesk.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert")
public class Alert extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String source;

    private String timeAgo;

    /** active / resolved */
    private String status;
}
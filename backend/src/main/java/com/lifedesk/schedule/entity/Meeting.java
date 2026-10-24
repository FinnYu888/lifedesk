package com.lifedesk.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会议/日程。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting")
public class Meeting extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMin;

    private Integer attendees;

    /** pending / current / done */
    private String status;

    /** 1=今日, 0=非今日 */
    private Integer isToday;
}
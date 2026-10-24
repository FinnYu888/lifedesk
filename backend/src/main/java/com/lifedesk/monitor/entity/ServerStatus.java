package com.lifedesk.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务状态。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("server_status")
public class ServerStatus extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer latency;

    /** running/checking/alert/timeout */
    private String status;

    private String latencyUnit;
}
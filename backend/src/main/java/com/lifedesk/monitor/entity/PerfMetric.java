package com.lifedesk.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 24h 性能指标采样点。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("perf_metric")
public class PerfMetric extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 时间点文案，如 00:00 */
    private String pointTime;

    private Double cpu;

    private Double memory;

    private Double latency;
}
package com.lifedesk.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.health.entity.BodyMetric;
import org.apache.ibatis.annotations.Mapper;

/**
 * 身体指标 Mapper。
 *
 * @author LifeDesk
 */
@Mapper
public interface BodyMetricMapper extends BaseMapper<BodyMetric> {
}

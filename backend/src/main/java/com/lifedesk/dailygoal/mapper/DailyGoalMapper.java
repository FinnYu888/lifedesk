package com.lifedesk.dailygoal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.dailygoal.entity.DailyGoal;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日目标 Mapper。
 *
 * @author LifeDesk
 */
@Mapper
public interface DailyGoalMapper extends BaseMapper<DailyGoal> {
}

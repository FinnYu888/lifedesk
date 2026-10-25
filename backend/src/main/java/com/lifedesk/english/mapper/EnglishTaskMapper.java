package com.lifedesk.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.english.entity.EnglishTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 英语提升任务 Mapper。
 *
 * @author LifeDesk
 */
@Mapper
public interface EnglishTaskMapper extends BaseMapper<EnglishTask> {
}

package com.lifedesk.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.english.entity.EnglishPunch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 英语每日打卡记录 Mapper。
 *
 * @author LifeDesk
 */
@Mapper
public interface EnglishPunchMapper extends BaseMapper<EnglishPunch> {
}

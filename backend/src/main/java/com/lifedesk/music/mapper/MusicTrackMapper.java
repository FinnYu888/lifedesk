package com.lifedesk.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.music.entity.MusicTrack;
import org.apache.ibatis.annotations.Mapper;

/**
 * 音乐曲目 Mapper。
 *
 * @author LifeDesk
 */
@Mapper
public interface MusicTrackMapper extends BaseMapper<MusicTrack> {
}

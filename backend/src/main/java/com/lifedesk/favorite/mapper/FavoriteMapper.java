package com.lifedesk.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.favorite.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏与笔记 Mapper。
 *
 * @author LifeDesk
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}

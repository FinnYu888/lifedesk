package com.lifedesk.custommenu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.custommenu.entity.CustomMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自定义菜单 Mapper。
 *
 * @author LifeDesk
 */
@Mapper
public interface CustomMenuMapper extends BaseMapper<CustomMenu> {
}

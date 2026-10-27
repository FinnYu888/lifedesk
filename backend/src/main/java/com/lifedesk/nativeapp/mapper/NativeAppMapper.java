package com.lifedesk.nativeapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.nativeapp.entity.NativeApp;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自定义本地应用 Mapper。
 *
 * @author LifeDesk
 */
@Mapper
public interface NativeAppMapper extends BaseMapper<NativeApp> {
}

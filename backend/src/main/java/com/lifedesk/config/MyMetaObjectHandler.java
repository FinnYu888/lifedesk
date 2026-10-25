package com.lifedesk.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lifedesk.common.BaseEntity;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器。
 *
 * <p>为 {@link BaseEntity#getCreateTime()} / {@link BaseEntity#getUpdateTime()} 自动填充时间，
 * 避免项目表 createTime 为 null 导致 new_repos 等按时间过滤的统计错位。</p>
 *
 * <ul>
 *   <li><b>INSERT</b>：createTime + updateTime 都设为当前时间</li>
 *   <li><b>UPDATE</b>：仅 updateTime 设为当前时间</li>
 * </ul>
 *
 * <p>配合 {@link BaseEntity} 上的 {@code @TableField(fill = FieldFill.INSERT)} /
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 注解生效。</p>
 *
 * @author LifeDesk
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // 仅在字段为 null 时填充，避免覆盖业务方主动设的值
        Object createTime = getFieldValByName("createTime", metaObject);
        if (createTime == null) {
            strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        }
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (updateTime == null) {
            strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // UPDATE 时永远刷新 updateTime（即使业务方未传）
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}

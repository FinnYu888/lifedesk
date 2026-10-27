package com.lifedesk.favorite.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收藏与笔记。
 *
 * <p>type=link 时为"收藏"，value 字段存 URL，前端点击在新窗口打开跳转</p>
 * <p>type=note 时为"笔记"，value 字段存笔记正文，前端以折叠卡片展开显示</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("favorite")
public class Favorite extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名称：收藏网站名 / 笔记标题 */
    private String name;

    /** 值：URL（type=link）/ 笔记正文（type=note） */
    private String value;

    /** link=收藏 / note=笔记 */
    private String type;

    /** 备注/分类，可选；置 null 表示清空（IGNORED 策略保证 updateById 能写入 null） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String description;

    /** 环境标识：test=测试 / integration=集成 / prod=生产（仅 type=link 有意义） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String env;

    /** CDS 版本：3.0 / 5.0（仅 type=link 有意义；前端红绿区分避免选错） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String cdsVersion;

    /** 网页截图 URL / 本地图片路径，可选；为空时前端兜底用公开截图服务 mShots 自动生成 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String screenshot;
}

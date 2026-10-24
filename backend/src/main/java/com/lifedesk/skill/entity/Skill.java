package com.lifedesk.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能。
 *
 * <p>对应 04_Skills 工坊.md 中的 5 张技能卡。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill")
public class Skill extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** diamond / doubleRing / triangle / square / star */
    private String icon;

    /** 等级 1-5 */
    private Integer level;

    /** 成长阶段文案 */
    private String stage;

    /** 当前等级内进度 0-100 */
    private Integer progress;

    private String description;
}
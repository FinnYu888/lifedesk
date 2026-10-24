package com.lifedesk.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目实体。
 *
 * <p>对应 03_项目管理.md 中的活跃项目列表。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String tech;

    private String description;

    /** 完成率 0-100 */
    private Integer progress;

    /** 最近活跃时间文案，如 "2h 前" */
    private String lastActive;

    /** 状态：normal / pending */
    private String status;
}
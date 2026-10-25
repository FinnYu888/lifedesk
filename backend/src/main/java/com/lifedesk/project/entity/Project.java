package com.lifedesk.project.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 项目实体。
 *
 * <p>对应 03_项目管理.md 中的活跃项目列表。</p>
 * <p>扩展字段支持填写 git 地址/用户名/密码/分支，并自动拉取提交/PR/发布数据。</p>
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

    /** Git 仓库地址，如 https://github.com/org/repo.git */
    private String gitUrl;

    /** Git 鉴权用户名 */
    private String gitUsername;

    /** Git 鉴权密码或 Access Token */
    private String gitPassword;

    /** 使用分支 */
    private String gitBranch;

    /** 仓库类型：github / gitlab / generic */
    private String gitPlatform;

    /** 最近一次同步时间 */
    private LocalDateTime lastSyncAt;

    /** 最近一次同步拉取的提交数 */
    private Integer commitsCount;

    /** PR 总数 */
    private Integer prsCount;

    /** 发布(标签)总数 */
    private Integer releasesCount;

    /** 最近一次同步状态：success / failed / syncing */
    private String syncStatus;

    /** 最近一次同步失败的错误信息（syncStatus=failed 时填充）。成功时被显式置 NULL，所以 updateStrategy=IGNORED 让 null 也写入 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String syncError;
}

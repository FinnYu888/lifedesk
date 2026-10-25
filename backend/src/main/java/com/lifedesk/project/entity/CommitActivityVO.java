package com.lifedesk.project.entity;

import com.lifedesk.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提交动态 VO。
 *
 * <p>继承 {@link CommitActivity}，附加 {@link #projectName} 字段，
 * 用于"最近提交活动"列表展示每个项目的最后一次提交 + 项目名。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("commit_activity")
public class CommitActivityVO extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String author;

    private String message;

    /** feat/fix/chore/refactor/docs/merge */
    private String type;

    private String timeAgo;

    private LocalDateTime committedAt;

    /** commit sha，用于幂等去重 */
    private String sha;

    /** 关联项目名（自定义 SQL JOIN 时填充，非表字段） */
    @TableField(exist = false)
    private String projectName;
}

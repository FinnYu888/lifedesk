package com.lifedesk.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提交动态。
 *
 * <p>扩展 project_id 关联项目，sha 用于同步去重。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("commit_activity")
public class CommitActivity extends BaseEntity {

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
}

package com.lifedesk.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Git 合并请求（来自 merge commit 解析）。
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pull_request")
public class PullRequest extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** PR 编号，如 #123 */
    private String number;

    private String title;

    private String author;

    /** merged/open/closed */
    private String state;

    private String sourceBranch;

    private String targetBranch;

    private LocalDateTime mergedAt;

    private String message;
}

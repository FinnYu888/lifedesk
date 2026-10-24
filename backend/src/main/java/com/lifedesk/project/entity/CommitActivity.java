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
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("commit_activity")
public class CommitActivity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String author;

    private String message;

    /** feat/fix/chore/refactor/docs */
    private String type;

    private String timeAgo;

    private LocalDateTime committedAt;
}
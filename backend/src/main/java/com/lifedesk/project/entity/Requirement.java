package com.lifedesk.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 我的需求。
 *
 * <p>从第三方需求平台 {@code https://wetrans.cimc.cn/gateway/apiIssue/developTask/query} 拉取。
 * 字段对齐该接口的返回：Sprint / 人/天 / 任务状态 / 关联话题 / 任务详情 / 产品 / 测试人。</p>
 *
 * <p><b>Sprint 编号规则</b>：形如 {@code CDS20261025}，前缀 CDS 之后 8 位即里程碑时间节点（版本）。
 * 里程碑进度统计据此按 sprint 聚合：进度 = 该 sprint 下 done 任务数 / (done + doing + todo 任务数)。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("requirement")
public class Requirement extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Sprint 版本号，如 CDS20261025 */
    private String sprint;

    /** 人/天估时 */
    private BigDecimal personDay;

    /** 任务状态：todo / doing / done / cancel */
    private String taskStatus;

    /** 关联话题 */
    private String topic;

    /** 任务详情 */
    private String taskDetail;

    /** 产品 */
    private String product;

    /** 测试人 */
    private String tester;

    /** 第三方平台需求 ID，便于幂等去重（接口对接时使用） */
    private String sourceId;
}

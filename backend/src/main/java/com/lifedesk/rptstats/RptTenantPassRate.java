package com.lifedesk.rptstats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 单个租户（盐田/蛇口）的用例通过率聚合结果。
 *
 * @author LifeDesk
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RptTenantPassRate {

    /** RPT 租户 id（29=盐田堆场，30=蛇口堆场） */
    private Integer tenantId;

    /** 租户名，取 RPT 登录响应的 tenantName，如 "盐田堆场" */
    private String tenantName;

    /** 该租户是否拉取成功；false 时前端展示 error 提示 */
    private boolean success;

    /** 拉取失败原因（success=false 时有值） */
    private String error;

    /** 各业务模块的用例通过率 */
    private List<RptModuleStat> modules;
}

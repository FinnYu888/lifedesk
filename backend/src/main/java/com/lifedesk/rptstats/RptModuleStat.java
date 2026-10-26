package com.lifedesk.rptstats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * RPT 平台单个业务模块的用例通过率统计。
 *
 * <p>数据源：{@code GET {rpt.remote.base-url}/api/stats/modules}，响应 {@code data.modules[]} 元素。</p>
 *
 * @author LifeDesk
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RptModuleStat {

    /** 业务模块英文标识，如 USER / GATE / ORDER */
    private String bizModule;

    /** 业务模块中文名，如 用户 / 闸口中控 / 订单 */
    private String bizModuleLabel;

    /** 用例总数 */
    private Integer totalCount;

    /** 通过数 */
    private Integer passedCount;

    /** 失败数 */
    private Integer failedCount;

    /** 通过率（百分数，如 93.38） */
    private Double passRate;

    /** 该模块下的用例明细（最近运行状态），供前端点击展开查看；失败用例由前端优先排序 */
    private List<RptCaseStat> cases;
}

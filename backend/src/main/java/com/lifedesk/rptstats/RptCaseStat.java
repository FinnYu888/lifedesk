package com.lifedesk.rptstats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * RPT 平台单条测试用例的最近运行状态。
 *
 * <p>数据源：{@code GET {rpt.remote.base-url}/api/stats/modules}，响应 {@code data.modules[].cases[]} 元素。</p>
 *
 * @author LifeDesk
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RptCaseStat {

    /** 用例 id */
    private Long id;

    /** 用例名，如 "修改 · PUT /yard" */
    private String name;

    /** 用例类型，如 COMPOSITE */
    private String caseType;

    /** 最近一次运行状态：PASSED / FAILED / ... */
    private String lastStatus;

    /** 最近一次运行完成时间，如 "2026-10-26 02:00:01" */
    private String lastFinishedAt;
}

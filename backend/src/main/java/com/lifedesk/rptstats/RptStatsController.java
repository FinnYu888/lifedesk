package com.lifedesk.rptstats;

import com.lifedesk.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RPT 用例通过率 Controller。
 *
 * <p>路由 /api/rpt-stats：
 * <ul>
 *   <li>GET /rpt-stats/pass-rate  双租户（盐田29/蛇口30）用例通过率聚合</li>
 * </ul>
 * </p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/rpt-stats")
@RequiredArgsConstructor
public class RptStatsController {

    private final RptStatsService rptStatsService;

    /** 双租户用例通过率：[{tenantId, tenantName, success, error, modules:[{bizModuleLabel, totalCount, passedCount, failedCount, passRate}]}] */
    @GetMapping("/pass-rate")
    public R<List<RptTenantPassRate>> passRate() {
        return R.ok(rptStatsService.fetchPassRate());
    }
}

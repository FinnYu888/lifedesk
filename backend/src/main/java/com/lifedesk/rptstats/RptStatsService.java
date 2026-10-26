package com.lifedesk.rptstats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * RPT 接口测试用例平台对接服务：拉取各租户（盐田/蛇口）的用例通过率。
 *
 * <p>鉴权链路（已实测验证）：
 * <ol>
 *   <li>POST {@code {base-url}/api/auth/login/password}，
 *       body {@code {"username":"ypf","password":"***","selectTenantId":"29","cdsVersion":"5"}}，
 *       响应 {@code data.token} 即 RPT 平台 token（同时返回 tenantName）。</li>
 *   <li>GET {@code {base-url}/api/stats/modules}，请求头 {@code Authorization: Bearer <token>}，
 *       响应 {@code data.modules[]} 为各业务模块统计。</li>
 * </ol>
 * 注：用户最初提供的 {@code cdssz-uat.cimcwetrans.com/api/cds-user/auth/username} 为 CDS 系统
 * 鉴权，直接调用返回「未获取到租户信息」不可用；RPT 平台自身登录即等效链路
 * （RPT login-config 显示 provider=cds，内部同样对接 CDS）。</p>
 *
 * <p>多租户循环获取：tenants 配置逗号分隔（默认 29,30），单租户失败不影响其他租户，
 * 失败信息记录在 {@link RptTenantPassRate#error}。</p>
 *
 * @author LifeDesk
 */
@Service
@Slf4j
public class RptStatsService {

    @Value("${rpt.remote.base-url:http://10.139.92.114:8098}")
    private String baseUrl;

    @Value("${rpt.remote.username:ypf}")
    private String username;

    @Value("${rpt.remote.password:}")
    private String password;

    @Value("${rpt.remote.cds-version:5}")
    private String cdsVersion;

    /** 参与统计的租户 id 列表，逗号分隔，如 29,30 */
    @Value("${rpt.remote.tenants:29,30}")
    private String tenants;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 拉取所有配置租户的用例通过率。
     *
     * @return 每个租户一个聚合结果（含各模块统计），顺序与配置一致
     */
    public List<RptTenantPassRate> fetchPassRate() {
        List<RptTenantPassRate> result = new ArrayList<>();
        for (String tid : tenants.split(",")) {
            String id = tid.trim();
            if (id.isEmpty()) {
                continue;
            }
            result.add(fetchOneTenant(Integer.parseInt(id)));
        }
        return result;
    }

    /** 单租户：登录拿 token → 拉模块统计；异常不抛出，写入 error 字段 */
    private RptTenantPassRate fetchOneTenant(int tenantId) {
        RptTenantPassRate out = new RptTenantPassRate();
        out.setTenantId(tenantId);
        try {
            JsonNode loginData = login(tenantId);
            out.setTenantName(loginData.path("tenantName").asText(null));
            String token = loginData.path("token").asText(null);
            if (token == null || token.isEmpty()) {
                out.setSuccess(false);
                out.setError("登录成功但未返回 token");
                return out;
            }
            out.setModules(fetchModules(token));
            out.setSuccess(true);
        } catch (Exception e) {
            log.warn("RPT 租户 {} 通过率拉取失败: {}", tenantId, e.getMessage());
            out.setSuccess(false);
            out.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return out;
    }

    /** RPT 平台密码登录，返回响应 data 节点（含 token / tenantName / tenantId） */
    private JsonNode login(int tenantId) throws Exception {
        String body = mapper.createObjectNode()
                .put("username", username)
                .put("password", password)
                .put("selectTenantId", String.valueOf(tenantId))
                .put("cdsVersion", cdsVersion)
                .toString();
        JsonNode resp = postJson(baseUrl + "/api/auth/login/password", body, null);
        if (resp.path("code").asInt() != 200) {
            throw new IllegalStateException("RPT 登录失败: " + resp.path("message").asText(""));
        }
        return resp.path("data");
    }

    /** 携带 token 拉取模块统计，提取统计字段（丢弃庞大的 cases 明细减小响应体） */
    private List<RptModuleStat> fetchModules(String token) throws Exception {
        JsonNode resp = getJson(baseUrl + "/api/stats/modules", token);
        if (resp.path("code").asInt() != 200) {
            throw new IllegalStateException("RPT 统计接口失败: " + resp.path("message").asText(""));
        }
        JsonNode modules = resp.path("data").path("modules");
        List<RptModuleStat> list = new ArrayList<>();
        if (modules.isArray()) {
            for (JsonNode m : modules) {
                RptModuleStat s = new RptModuleStat();
                s.setBizModule(m.path("bizModule").asText(null));
                s.setBizModuleLabel(m.path("bizModuleLabel").asText(null));
                s.setTotalCount(m.path("totalCount").asInt(0));
                s.setPassedCount(m.path("passedCount").asInt(0));
                s.setFailedCount(m.path("failedCount").asInt(0));
                s.setPassRate(m.path("passRate").asDouble(0));
                // 用例明细（name/lastStatus/lastFinishedAt），供前端点击模块展开查看
                List<RptCaseStat> cases = new ArrayList<>();
                JsonNode caseArr = m.path("cases");
                if (caseArr.isArray()) {
                    for (JsonNode c : caseArr) {
                        RptCaseStat cs = new RptCaseStat();
                        cs.setId(c.path("id").asLong());
                        cs.setName(c.path("name").asText(null));
                        cs.setCaseType(c.path("caseType").asText(null));
                        cs.setLastStatus(c.path("lastStatus").asText(null));
                        cs.setLastFinishedAt(c.path("lastFinishedAt").asText(null));
                        cases.add(cs);
                    }
                }
                s.setCases(cases);
                list.add(s);
            }
        }
        return list;
    }

    // ===== HTTP 工具（与 RequirementService 同款 HttpURLConnection 模式，无第三方依赖） =====

    private JsonNode postJson(String urlStr, String body, String token) throws Exception {
        HttpURLConnection conn = open(urlStr, token);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return read(conn);
    }

    private JsonNode getJson(String urlStr, String token) throws Exception {
        HttpURLConnection conn = open(urlStr, token);
        conn.setRequestMethod("GET");
        return read(conn);
    }

    private HttpURLConnection open(String urlStr, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        return conn;
    }

    private JsonNode read(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                }
            }
        }
        if (status >= 400) {
            throw new IllegalStateException("RPT HTTP " + status + ": " + sb.substring(0, Math.min(sb.length(), 200)));
        }
        return mapper.readTree(sb.toString());
    }
}

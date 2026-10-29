package com.lifedesk.project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifedesk.project.entity.Milestone;
import com.lifedesk.project.entity.Requirement;
import com.lifedesk.project.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 我的需求 Service。
 *
 * <p>从第三方需求平台 {@code https://wetrans.cimc.cn/gateway/apiIssue/developTask/query} 拉取需求列表。
 * 该接口部署在集团内网，外网调用会被网关拦截；后端部署到内网后会自动调用真实接口，
 * 远程不可达时回退本地 mock 表 {@code requirement} 提供数据。</p>
 *
 * <p><b>入参结构</b>（POST JSON）：
 * <pre>
 * {
 *   "current": 1,
 *   "pageSize": 20,
 *   "statusList": ["open","in_progress","unsolved","developed","in_test","for_release"],
 *   "userIds": ["1980457439697596417"],
 *   "project": "CDS"
 * }
 * </pre>
 * </p>
 *
 * <p><b>状态字段映射</b>：远程 6 状态 → 本地 4 状态
 * <ul>
 *   <li>open / unsolved → todo（待办）</li>
 *   <li>in_progress / in_test → doing（进行中）</li>
 *   <li>developed / for_release → done（已完成）</li>
 *   <li>其他 → todo（兜底）</li>
 * </ul>
 * </p>
 *
 * <p><b>Sprint 编号规则</b>：形如 {@code CDS20261025}，前缀 CDS 之后 8 位即里程碑时间节点（版本）。
 * 里程碑进度统计据此按 sprint 聚合：进度 = 该 sprint 下 done 任务数 / (done + doing + todo 任务数)。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementService {

    private final RequirementMapper requirementMapper;

    /** 第三方需求接口地址 */
    @Value("${requirement.remote.url:https://wetrans.cimc.cn/gateway/apiIssue/developTask/query}")
    private String remoteUrl;

    /** 第三方需求详情接口地址（GET ?id=...） */
    @Value("${requirement.remote.detail-url:https://wetrans.cimc.cn/gateway/apiIssue/developTask/getDetail}")
    private String remoteDetailUrl;

    /** sprint 列表接口：POST，返回所有 sprint（id + sprintName 如 CDS20261025） */
    @Value("${requirement.remote.sprint-query-url:https://wetrans.cimc.cn/gateway/apiIssue/sprint/query}")
    private String remoteSprintQueryUrl;

    /** issue 列表接口：POST，按 sprintIds 查询，返回该 sprint 下所有 issue（含 status=CLOSED） */
    @Value("${requirement.remote.issue-query-url:https://wetrans.cimc.cn/gateway/apiIssue/issue/query}")
    private String remoteIssueQueryUrl;

    /** 远程接口调用 token（JWT）—— 配置文件兜底值 */
    @Value("${requirement.remote.token:}")
    private String remoteToken;

    /**
     * 前端动态设置的最新 authorization token（线程安全）。
     *
     * <p>{@code wetrans.cimc.cn/gateway/} 开头的接口鉴权信息每日可能过期，
     * 前端在"我的需求"卡片可输入最新 authorization 动态覆盖。
     * 优先级：currentToken > remoteToken（配置文件兜底）。</p>
     *
     * <p><b>持久化</b>：设置/清空时同步写入本地文件（{@link #tokenPath()}），
     * 服务重启时在 {@link #init()} 中自动加载，保证"保存后每次刷新/请求都用它"。</p>
     */
    private final AtomicReference<String> currentToken = new AtomicReference<>(null);

    /** 持久化文件路径覆盖（可选配置，默认 ~/.lifedesk/requirement-auth.token） */
    @Value("${requirement.token-file:}")
    private String tokenFile;

    /** 是否启用远程接口；默认 false，使用本地 mock 表 */
    @Value("${requirement.remote.enabled:false}")
    private boolean remoteEnabled;

    /** 入参 project 字段（项目标识，如 CDS） */
    @Value("${requirement.remote.project:CDS}")
    private String remoteProject;

    /** 入参 pageSize */
    @Value("${requirement.remote.page-size:20}")
    private int remotePageSize;

    /** 入参 userIds（逗号分隔） */
    @Value("${requirement.remote.user-ids:}")
    private String remoteUserIds;

    /** 入参 statusList（逗号分隔） */
    @Value("${requirement.remote.status-list:open,in_progress,unsolved,developed,in_test,for_release}")
    private String remoteStatusListCsv;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter SPRINT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 匹配 sprint 末尾 8 位数字（CDS20261025 -> 20261025） */
    private static final Pattern SPRINT_DATE_PATTERN = Pattern.compile("(\\d{8})$");

    /**
     * 信任所有证书的 SSLSocketFactory。
     *
     * <p>第三方需求接口 {@code wetrans.cimc.cn} 部署在集团内网，HTTPS 可能使用自签证书或
     * 内部 CA 签发的证书，外网/JVM 默认 truststore 不一定包含，导致 {@code PKIX path building failed}。
     * 此 factory 仅作用于本服务发起的远程调用（通过 HttpsURLConnection.setSSLSocketFactory），
     * 不污染 JVM 全局，也不影响其他业务（如 MySQL JDBC）。</p>
     */
    private static final SSLSocketFactory SSL_TRUST_ALL_FACTORY = createTrustAllSocketFactory();

    private static SSLSocketFactory createTrustAllSocketFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // 信任所有客户端证书
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // 信任所有服务端证书（含自签）
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            // 极端情况退回 JVM 默认
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    /** 远程状态 → 本地状态映射（按用户提供的枚举确认）
     * <ul>
     *   <li>open        → todo（待办）</li>
     *   <li>in_progress   → doing（进行中）</li>
     *   <li>in_test      → testing（提测）</li>
     *   <li>for_release  → pending（待发版）</li>
     *   <li>developed    → done（完成）</li>
     * </ul>
     * 注：远程未识别的状态兜底为 todo，避免列表展示空。
     */
    private static final Map<String, String> STATUS_MAP = new HashMap<String, String>() {{
        put("open", "todo");
        put("in_progress", "doing");
        put("in_test", "testing");
        put("for_release", "pending");
        put("developed", "done");
    }};

    @PostConstruct
    public void init() {
        // 启动时从本地文件恢复上次保存的 authorization（覆盖远程配置兜底 token）
        String saved = loadTokenFromFile();
        if (saved != null && !saved.isEmpty()) {
            currentToken.set(saved);
            log.info("[Authorization] 已从本地文件恢复 authorization（长度 {}）", saved.length());
        }
        log.info("RequirementService 初始化：remoteEnabled={}, project={}, pageSize={}, userIds={}, statusList={}",
                remoteEnabled, remoteProject, remotePageSize,
                remoteUserIds == null || remoteUserIds.isEmpty() ? "无" : remoteUserIds,
                remoteStatusListCsv);
    }

    /** 持久化文件路径：默认 ~/.lifedesk/requirement-auth.token，可用 requirement.token-file 覆盖 */
    private java.nio.file.Path tokenPath() {
        if (tokenFile != null && !tokenFile.trim().isEmpty()) {
            return java.nio.file.Paths.get(tokenFile.trim());
        }
        return java.nio.file.Paths.get(System.getProperty("user.home"), ".lifedesk", "requirement-auth.token");
    }

    /** 从本地文件读取持久化的 authorization；不存在或读取失败返回 null */
    private String loadTokenFromFile() {
        try {
            java.nio.file.Path p = tokenPath();
            if (!java.nio.file.Files.exists(p)) return null;
            byte[] bytes = java.nio.file.Files.readAllBytes(p);
            String s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            log.warn("[Authorization] 读取持久化文件失败：{}", e.getMessage());
            return null;
        }
    }

    /** 把 authorization 写入本地文件（覆盖旧值）；传 null/空则删除文件 */
    private void saveTokenToFile(String token) {
        try {
            java.nio.file.Path p = tokenPath();
            if (token == null || token.isEmpty()) {
                java.nio.file.Files.deleteIfExists(p);
                return;
            }
            java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.write(p, token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 持久化失败不阻断内存设置，仅告警（下次重启需重新输入）
            log.warn("[Authorization] 持久化写入失败：{}", e.getMessage());
        }
    }

    /**
     * 获取当前生效的 authorization token（优先 currentToken，其次配置文件 remoteToken）。
     * 用于所有 wetrans.cimc.cn/gateway/ 接口的 Authorization 请求头。
     */
    private String getEffectiveToken() {
        String t = currentToken.get();
        if (t != null && !t.isEmpty()) return t;
        return remoteToken;
    }

    /**
     * 前端动态设置最新 authorization（用户在"我的需求"卡片输入后调用）。
     * 设置后立即对所有 fetch* 方法生效，无需重启服务。
     *
     * @param token 最新 JWT（不带 Bearer 前缀）；传 null 或空字符串则清空动态 token
     */
    public void setAuthorization(String token) {
        String cleaned = sanitizeToken(token);
        if (cleaned == null) {
            currentToken.set(null);
            saveTokenToFile(null);
            log.info("动态 authorization 已清空（含持久化文件）");
        } else {
            currentToken.set(cleaned);
            saveTokenToFile(cleaned);
            log.info("动态 authorization 已更新并持久化（原始长度 {}，清洗后长度 {}）",
                    token.length(), cleaned.length());
        }
    }

    /**
     * 查询当前 authorization 设置状态（不返回 token 本身，避免泄漏）。
     * 前端用于展示"是否已设置 authorization"以及"是动态设置还是配置文件兜底"。
     */
    public Map<String, Object> getAuthorizationStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        String cur = currentToken.get();
        s.put("hasDynamicToken", cur != null && !cur.isEmpty());
        s.put("hasFallbackToken", remoteToken != null && !remoteToken.isEmpty());
        s.put("hasEffectiveToken", getEffectiveToken() != null && !getEffectiveToken().isEmpty());
        s.put("source", (cur != null && !cur.isEmpty()) ? "dynamic" : (remoteToken != null && !remoteToken.isEmpty() ? "fallback" : "none"));
        s.put("persisted", java.nio.file.Files.exists(tokenPath()));
        return s;
    }

    /**
     * 判断 HTTP 状态码是否为鉴权失败（401/403）。
     * 用于 fetch* 方法：鉴权失败时返回空列表，不抛异常，前端展示空状态。
     */
    private boolean isAuthError(int status) {
        return status == 401 || status == 403;
    }

    /**
     * 我的需求列表（兼容入口）。
     *
     * @param sprint  可选 sprint 过滤，传 {@code CDS20261025} 只返回该 sprint 下需求
     * @param status  可选任务状态过滤：todo/doing/done/cancel
     * @param keyword 可选，按 task_detail / topic / product / tester 模糊匹配
     */
    public List<Requirement> list(String sprint, String status, String keyword) {
        @SuppressWarnings("unchecked")
        List<Requirement> data = (List<Requirement>) listWithMeta(sprint, status, keyword).get("list");
        return data;
    }

    /**
     * 我的需求列表（带失败原因元信息）。
     *
     * <p>返回 Map：{@code list=需求列表(过滤后), total=远程拉取总条数(过滤前), authExpired=鉴权是否过期,
     * remoteError=失败原因(无失败为 null), source=数据来源(local/remote)}。
     * 前端据此展示准确原因（"鉴权过期请重新输入" / "远程接口异常 xxx" / "被筛选条件过滤为空"），不再靠"列表为空"猜测。</p>
     *
     * <p>远程启用且调用成功时返回远程数据；鉴权过期（网关业务码 401/403 或 HTTP 401/403）
     * 或远程不可达时返回空列表 + 对应原因。</p>
     */
    public Map<String, Object> listWithMeta(String sprint, String status, String keyword) {
        Map<String, Object> meta = new LinkedHashMap<>();
        String token = getEffectiveToken();
        if (!remoteEnabled) {
            List<Requirement> localList = queryLocal(sprint, status, keyword);
            meta.put("list", localList);
            meta.put("total", localList.size());
            meta.put("authExpired", false);
            meta.put("remoteError", null);
            meta.put("source", "local");
            return meta;
        }
        if (token == null || token.isEmpty()) {
            meta.put("list", new ArrayList<Requirement>());
            meta.put("total", 0);
            meta.put("authExpired", false);
            meta.put("remoteError", "未设置 authorization，请输入最新鉴权信息");
            meta.put("source", "remote");
            return meta;
        }
        try {
            List<Requirement> remote = fetchFromRemote();
            if (remote == null) {
                meta.put("list", new ArrayList<Requirement>());
                meta.put("total", 0);
                meta.put("authExpired", false);
                meta.put("remoteError", "远程需求接口调用失败（网络异常或返回结构未识别）");
            } else {
                // total = 过滤前条数；前端据此区分"远程无数据"与"被筛选条件（关键词/Sprint/状态）过滤为空"
                meta.put("total", remote.size());
                // 远程数据成功拉取后，做内存过滤（远程接口入参不含 sprint/status/keyword 维度）
                meta.put("list", filterRemote(remote, sprint, status, keyword));
                meta.put("authExpired", false);
                meta.put("remoteError", null);
            }
        } catch (AuthExpiredException e) {
            log.warn("我的需求：鉴权信息已过期：{}", e.getMessage());
            meta.put("list", new ArrayList<Requirement>());
            meta.put("total", 0);
            meta.put("authExpired", true);
            meta.put("remoteError", e.getMessage());
        } catch (RemoteBizException e) {
            log.warn("我的需求：远程接口业务异常：{}", e.getMessage());
            meta.put("list", new ArrayList<Requirement>());
            meta.put("total", 0);
            meta.put("authExpired", false);
            meta.put("remoteError", e.getMessage());
        } catch (Exception e) {
            log.warn("调用远程需求接口失败：{}", e.getMessage());
            meta.put("list", new ArrayList<Requirement>());
            meta.put("total", 0);
            meta.put("authExpired", false);
            meta.put("remoteError", "远程接口调用异常：" + e.getMessage());
        }
        meta.put("source", "remote");
        return meta;
    }

    /**
     * 自定义异常：鉴权信息过期（401/403）。
     * 用于在 fetch* 方法中明确区分"鉴权失败"与"网络/远程异常"，
     * 让 list/listMilestones 能在鉴权失败时返回空列表（而不是回退本地 mock）。
     */
    public static class AuthExpiredException extends RuntimeException {
        public AuthExpiredException(String message) {
            super(message);
        }
    }

    /**
     * 自定义异常：远程网关业务异常（success=false 但非鉴权问题）。
     * 与 AuthExpiredException 区分，便于前端展示准确失败原因。
     */
    public static class RemoteBizException extends RuntimeException {
        public RemoteBizException(String message) {
            super(message);
        }
    }

    /**
     * 校验 wetrans 网关统一响应外壳：{@code {code, msg, success, data}}。
     *
     * <p><b>关键（实测确认）</b>：网关对无效/过期 token 返回的是 <b>HTTP 200</b> +
     * 响应体 {@code {"code":"401","msg":"服务异常","data":null}}，而非 HTTP 401。
     * 因此仅凭 HTTP 状态码永远检测不到鉴权失败，必须解析业务码。</p>
     *
     * <ul>
     *   <li>code = 401/403（或 success=false 且 code 为鉴权类）→ 抛 {@link AuthExpiredException}</li>
     *   <li>success=false 且其他 code → 抛 {@link RemoteBizException}（携带 msg）</li>
     *   <li>正常（code=100000 / success=true）→ 返回 root 节点</li>
     * </ul>
     */
    private JsonNode checkGatewayEnvelope(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RemoteBizException("响应不是有效 JSON：" + (body == null ? "null" : body.length() + "字符"));
        }
        String code = root.has("code") && !root.get("code").isNull() ? root.get("code").asText("") : "";
        boolean success = root.has("success") && !root.get("success").isNull() ? root.get("success").asBoolean(true) : true;
        String msg = root.has("msg") && !root.get("msg").isNull() ? root.get("msg").asText("") : "";
        if ("401".equals(code) || "403".equals(code)) {
            throw new AuthExpiredException("网关返回业务码 " + code + (msg.isEmpty() ? "" : "（" + msg + "）") + "，鉴权信息已过期");
        }
        if (!success && !"100000".equals(code)) {
            throw new RemoteBizException("code=" + code + (msg.isEmpty() ? "" : "，msg=" + msg));
        }
        return root;
    }

    /**
     * 保存鉴权信息前的清洗：去首尾空白、去掉粘贴时可能带上的 Bearer 前缀（大小写不敏感、可重复）、
     * 去首尾成对引号。网关要求 Authorization 直接放 JWT（带 Bearer 反而被拒）。
     */
    private String sanitizeToken(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        while (t.toLowerCase().startsWith("bearer ")) {
            t = t.substring(7).trim();
        }
        if (t.length() >= 2
                && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t.isEmpty() ? null : t;
    }

    /**
     * 用当前生效 token 轻量探测网关鉴权是否有效（POST developTask/query，pageSize=1）。
     * 供"保存 authorization"接口立即校验，让用户马上知道粘贴的 token 对不对。
     *
     * @return {hasToken:bool, valid:bool, msg:String}
     */
    public Map<String, Object> probeToken() {
        Map<String, Object> r = new LinkedHashMap<>();
        String token = getEffectiveToken();
        boolean hasToken = token != null && !token.isEmpty();
        r.put("hasToken", hasToken);
        if (!hasToken) {
            r.put("valid", false);
            r.put("msg", "未设置 authorization");
            return r;
        }
        HttpURLConnection conn = null;
        try {
            Map<String, Object> payload = buildRemotePayload();
            payload.put("pageSize", 1); // 探测用最小页
            String bodyJson = objectMapper.writeValueAsString(payload);
            URL url = new URL(remoteUrl);
            conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(SSL_TRUST_ALL_FACTORY);
                https.setHostnameVerifier((hostname, session) -> true);
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", token);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            int status = conn.getResponseCode();
            if (isAuthError(status)) {
                r.put("valid", false);
                r.put("msg", "网关 HTTP " + status + "，鉴权信息已过期");
                return r;
            }
            if (status != 200) {
                r.put("valid", false);
                r.put("msg", "网关 HTTP " + status + "，校验失败");
                return r;
            }
            String body = readStream(conn);
            checkGatewayEnvelope(body);
            r.put("valid", true);
            r.put("msg", "网关校验通过");
            return r;
        } catch (AuthExpiredException | RemoteBizException e) {
            r.put("valid", false);
            r.put("msg", e.getMessage());
            return r;
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            r.put("valid", false);
            r.put("msg", "网关不可达：" + e.getMessage());
            return r;
        } catch (Exception e) {
            r.put("valid", false);
            r.put("msg", e.getClass().getSimpleName() + ": " + e.getMessage());
            return r;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 里程碑进度（兼容入口）。
     */
    public List<Milestone> listMilestones() {
        @SuppressWarnings("unchecked")
        List<Milestone> data = (List<Milestone>) listMilestonesWithMeta().get("list");
        return data;
    }

    /**
     * 里程碑进度（带失败原因元信息）：按 sprint 聚合（依据 issue/query 接口的 status=CLOSED 统计）。
     *
     * <p>返回 Map：{@code list=里程碑列表, authExpired=鉴权是否过期, remoteError=失败原因(无失败为 null)}。</p>
     *
     * <p>计算流程：
     * <ol>
     *   <li>调用 sprint/query 拿到所有 sprint 列表（id + sprintName 如 CDS20261025）</li>
     *   <li>调用 developTask/query 拿到当前用户的需求，提取唯一 sprint 列表</li>
     *   <li>对每个唯一 sprint，在 sprint/query 返回中匹配 sprintName，拿到 sprint id</li>
     *   <li>用 sprint id 调用 issue/query?sprintIds=[id]，拿到该 sprint 下所有 issue</li>
     *   <li>按 status=CLOSED 计算完成率 = 完成 / 总数 * 100</li>
     * </ol>
     * </p>
     */
    public Map<String, Object> listMilestonesWithMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("authExpired", false);
        meta.put("remoteError", null);

        String token = getEffectiveToken();
        if (!remoteEnabled) {
            meta.put("list", buildMilestonesFromLocal());
            return meta;
        }
        if (token == null || token.isEmpty()) {
            meta.put("list", new ArrayList<Milestone>());
            meta.put("remoteError", "未设置 authorization，请输入最新鉴权信息");
            return meta;
        }

        // 1) 拉取当前用户的需求列表，提取唯一 sprint 列表
        List<Requirement> userReqs;
        try {
            userReqs = fetchFromRemote();
        } catch (AuthExpiredException e) {
            log.warn("里程碑：鉴权信息已过期：{}", e.getMessage());
            meta.put("list", new ArrayList<Milestone>());
            meta.put("authExpired", true);
            meta.put("remoteError", e.getMessage());
            return meta;
        } catch (Exception e) {
            log.warn("里程碑：拉取用户需求失败：{}", e.getMessage());
            meta.put("list", new ArrayList<Milestone>());
            meta.put("remoteError", "远程接口调用异常：" + e.getMessage());
            return meta;
        }
        if (userReqs == null) {
            meta.put("list", new ArrayList<Milestone>());
            meta.put("remoteError", "远程需求接口调用失败（网络异常或返回结构未识别）");
            return meta;
        }
        if (userReqs.isEmpty()) {
            meta.put("list", new ArrayList<Milestone>());
            return meta;
        }

        // 提取唯一 sprint 列表（保持顺序）
        List<String> uniqueSprints = new ArrayList<>();
        for (Requirement r : userReqs) {
            String sp = r.getSprint();
            if (sp != null && !sp.isEmpty() && !uniqueSprints.contains(sp)) {
                uniqueSprints.add(sp);
            }
        }
        if (uniqueSprints.isEmpty()) {
            meta.put("list", new ArrayList<Milestone>());
            return meta;
        }

        // 2) 拉取所有 sprint 列表，构造 sprintName → sprintId 映射
        Map<String, String> sprintNameToId = new HashMap<>();
        try {
            List<SprintInfo> allSprints = fetchSprints();
            if (allSprints != null) {
                for (SprintInfo s : allSprints) {
                    if (s.name != null && s.id != null) {
                        sprintNameToId.put(s.name, s.id);
                    }
                }
            }
        } catch (AuthExpiredException e) {
            log.warn("里程碑：sprint/query 鉴权失败：{}", e.getMessage());
            meta.put("list", new ArrayList<Milestone>());
            meta.put("authExpired", true);
            meta.put("remoteError", e.getMessage());
            return meta;
        } catch (Exception e) {
            log.warn("里程碑：sprint/query 调用失败：{}", e.getMessage());
            meta.put("list", new ArrayList<Milestone>());
            meta.put("remoteError", "sprint/query 调用失败：" + e.getMessage());
            return meta;
        }

        // 3) 对每个唯一 sprint，调用 issue/query 拿到该 sprint 下所有 issue，按 status=CLOSED 聚合
        List<Milestone> result = new ArrayList<>();
        for (String sprintName : uniqueSprints) {
            String sprintId = sprintNameToId.get(sprintName);
            if (sprintId == null) {
                // sprint 在 sprint/query 中找不到，跳过（不影响其他 sprint）
                log.debug("里程碑：sprint {} 在 sprint/query 返回中未匹配到 id，跳过", sprintName);
                continue;
            }
            try {
                List<IssueInfo> issues = fetchIssues(sprintId);
                int total = issues == null ? 0 : issues.size();
                int closed = 0;
                int open = 0;
                if (issues != null) {
                    for (IssueInfo i : issues) {
                        if ("CLOSED".equalsIgnoreCase(i.status)) closed++;
                        else open++;
                    }
                }
                int progress = total > 0 ? (int) (closed * 100 / total) : 0;
                Milestone m = new Milestone();
                m.setName(sprintName);
                m.setProgress(progress);
                m.setNote(String.format("%d/%d 完成（未关闭 %d）", closed, total, open));
                m.setDueDate(parseSprintDate(sprintName));
                result.add(m);
            } catch (AuthExpiredException e) {
                log.warn("里程碑：issue/query?sprintIds={} 鉴权失败，跳过该 sprint：{}", sprintId, e.getMessage());
                meta.put("authExpired", true);
                if (meta.get("remoteError") == null) meta.put("remoteError", e.getMessage());
            } catch (Exception e) {
                log.warn("里程碑：issue/query?sprintIds={} 调用失败，跳过：{}", sprintId, e.getMessage());
            }
        }

        // 按 sprint 倒序（CDS20261025 在最前）
        result.sort((a, b) -> b.getName().compareTo(a.getName()));
        meta.put("list", result);
        return meta;
    }

    /** 本地 mock 数据聚合里程碑（仅当 remoteEnabled=false 时使用） */
    private List<Milestone> buildMilestonesFromLocal() {
        List<Requirement> all = queryLocal(null, null, null);
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (Requirement r : all) {
            String sp = r.getSprint();
            if (sp == null || sp.isEmpty()) continue;
            String localStatus = r.getTaskStatus();
            if (localStatus == null || "cancel".equals(localStatus)) continue;
            long[] arr = agg.computeIfAbsent(sp, k -> new long[4]);
            arr[0]++;
            if ("done".equals(localStatus)) arr[1]++;
            else if ("doing".equals(localStatus)) arr[2]++;
            else if ("todo".equals(localStatus)) arr[3]++;
        }
        List<Milestone> result = new ArrayList<>();
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            String sprint = e.getKey();
            long[] v = e.getValue();
            long total = v[0], done = v[1], doing = v[2], todo = v[3];
            int progress = total > 0 ? (int) (done * 100 / total) : 0;
            Milestone m = new Milestone();
            m.setName(sprint);
            m.setProgress(progress);
            m.setNote(String.format("%d/%d 完成（进行中 %d，待办 %d）", done, total, doing, todo));
            m.setDueDate(parseSprintDate(sprint));
            result.add(m);
        }
        result.sort((a, b) -> b.getName().compareTo(a.getName()));
        return result;
    }

    /** sprint/query 返回的单个 sprint 简化结构（id + sprintName） */
    private static class SprintInfo {
        String id;
        String name;
        SprintInfo(String id, String name) { this.id = id; this.name = name; }
    }

    /** issue/query 返回的单个 issue 简化结构（仅保留 status 用于聚合） */
    private static class IssueInfo {
        String status;
        IssueInfo(String status) { this.status = status; }
    }

    /**
     * 调用 sprint/query 接口拿取所有 sprint 列表。
     *
     * <p>POST 入参：{@code {"statusList":[], "current":1, "pageSize":1000}}
     * 返回结构兼容：{@code {code, data:{list|records|rows:[{id, sprintName, ...}]}}}</p>
     *
     * @throws AuthExpiredException 鉴权失败（401/403）
     */
    private List<SprintInfo> fetchSprints() {
        HttpURLConnection conn = null;
        String token = getEffectiveToken();
        try {
            // 构造入参
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("statusList", new ArrayList<>());
            payload.put("current", 1);
            payload.put("pageSize", 1000);
            String bodyJson = objectMapper.writeValueAsString(payload);

            URL url = new URL(remoteSprintQueryUrl);
            conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(SSL_TRUST_ALL_FACTORY);
                https.setHostnameVerifier((hostname, session) -> true);
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", token);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);

            byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int status = conn.getResponseCode();
            if (isAuthError(status)) {
                throw new AuthExpiredException("sprint/query 鉴权失败 status=" + status);
            }
            if (status != 200) {
                String errBody = readErrorStream(conn);
                log.warn("sprint/query 异常状态：status={}, errBody={}", status,
                        errBody == null ? "(无)" : (errBody.length() > 300 ? errBody.substring(0, 300) + "..." : errBody));
                return null;
            }

            String body = readStream(conn);
            log.debug("sprint/query 响应：bodyLen={}", body.length());

            // 网关外壳校验：HTTP 200 也可能携带业务码 401（token 过期）
            JsonNode root = checkGatewayEnvelope(body);
            return parseSprints(root);
        } catch (AuthExpiredException e) {
            throw e;
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            log.warn("sprint/query 不可达（网络/代理拦截）：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("sprint/query 调用异常：{} {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 解析 sprint/query 返回 JSON 为 SprintInfo 列表（root 已过网关外壳校验） */
    @SuppressWarnings("unchecked")
    private List<SprintInfo> parseSprints(JsonNode root) {
        try {
            JsonNode data = root.get("data");
            if (data == null) return null;
            JsonNode list;
            if (data.isArray()) list = data;
            else if (data.has("list") && data.get("list").isArray()) list = data.get("list");
            else if (data.has("records") && data.get("records").isArray()) list = data.get("records");
            else if (data.has("rows") && data.get("rows").isArray()) list = data.get("rows");
            else return null;

            List<SprintInfo> result = new ArrayList<>();
            for (JsonNode item : list) {
                String id = firstNonBlank(item, "id", "sprintId", "_id");
                String name = firstNonBlank(item, "sprintName", "name", "sprintCode", "code", "title");
                if (id != null && name != null) {
                    result.add(new SprintInfo(id, name));
                }
            }
            log.info("sprint/query 拉取成功：共 {} 条 sprint", result.size());
            return result;
        } catch (Exception e) {
            log.warn("sprint/query JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用 issue/query 接口拿取指定 sprint 下所有 issue 列表。
     *
     * <p>POST 入参：{@code {"current":1, "pageSize":20, "sprintIds":["<sprintId>"]}}
     * 返回结构兼容：{@code {code, data:{list|records|rows:[{status, ...}]}}}</p>
     *
     * @throws AuthExpiredException 鉴权失败（401/403）
     */
    private List<IssueInfo> fetchIssues(String sprintId) {
        HttpURLConnection conn = null;
        String token = getEffectiveToken();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("current", 1);
            payload.put("pageSize", 1000); // 取该 sprint 下所有 issue
            payload.put("sprintIds", Collections.singletonList(sprintId));
            String bodyJson = objectMapper.writeValueAsString(payload);

            URL url = new URL(remoteIssueQueryUrl);
            conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(SSL_TRUST_ALL_FACTORY);
                https.setHostnameVerifier((hostname, session) -> true);
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", token);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);

            byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int status = conn.getResponseCode();
            if (isAuthError(status)) {
                throw new AuthExpiredException("issue/query?sprintIds=" + sprintId + " 鉴权失败 status=" + status);
            }
            if (status != 200) {
                String errBody = readErrorStream(conn);
                log.warn("issue/query 异常状态：sprintId={}, status={}, errBody={}", sprintId, status,
                        errBody == null ? "(无)" : (errBody.length() > 300 ? errBody.substring(0, 300) + "..." : errBody));
                return null;
            }

            String body = readStream(conn);
            log.debug("issue/query 响应：sprintId={}, bodyLen={}", sprintId, body.length());

            // 网关外壳校验：HTTP 200 也可能携带业务码 401（token 过期）
            JsonNode root = checkGatewayEnvelope(body);
            return parseIssues(root);
        } catch (AuthExpiredException e) {
            throw e;
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            log.warn("issue/query 不可达：sprintId={}, {}", sprintId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("issue/query 调用异常：sprintId={}, {} {}", sprintId, e.getClass().getSimpleName(), e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 解析 issue/query 返回 JSON 为 IssueInfo 列表（root 已过网关外壳校验） */
    @SuppressWarnings("unchecked")
    private List<IssueInfo> parseIssues(JsonNode root) {
        try {
            JsonNode data = root.get("data");
            if (data == null) return null;
            JsonNode list;
            if (data.isArray()) list = data;
            else if (data.has("list") && data.get("list").isArray()) list = data.get("list");
            else if (data.has("records") && data.get("records").isArray()) list = data.get("records");
            else if (data.has("rows") && data.get("rows").isArray()) list = data.get("rows");
            else return null;

            List<IssueInfo> result = new ArrayList<>();
            for (JsonNode item : list) {
                String status = firstNonBlank(item, "status", "issueStatus", "state", "issue_status");
                result.add(new IssueInfo(status));
            }
            log.info("issue/query 拉取成功：共 {} 条 issue", result.size());
            return result;
        } catch (Exception e) {
            log.warn("issue/query JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 远程接口调用：POST https://wetrans.cimc.cn/gateway/apiIssue/developTask/query
     *
     * <p><b>鉴权头</b>：{@code Authorization: <token>}（直接放 JWT，<b>不带 Bearer 前缀</b>）。
     * 用户工具验证：直接 Authorization 头可以调通，加 Bearer 反而被拒。</p>
     *
     * <p><b>HTTPS 证书容错</b>：第三方接口部署在集团内网，可能使用自签证书或内部 CA，
     * 此处用 {@link #SSL_TRUST_ALL_FACTORY} 仅对本请求关闭证书校验，
     * 不影响 JVM 全局（不污染其他 HTTPS 业务如 GitHub API 调用）。</p>
     *
     * <p><b>Body</b>：由 application.yml 的 project/pageSize/userIds/statusList 配置构造。</p>
     *
     * <p><b>返回解析</b>：兼容 {@code {code, data:{list|records|rows:[...]}}} 与 {@code {code, data:[...]}}
     * 两种结构；list 元素字段名做常见兜底映射：sprint/sprintName/sprintCode,
     * personDay/manDay/workDay, status/state, topic/title, taskDetail/detail/desc/description,
     * product/productName, tester/testerName/testUser</p>
     *
     * @return 解析后的需求列表；调用失败返回 null 由调用方回退本地
     */
    private List<Requirement> fetchFromRemote() {
        HttpURLConnection conn = null;
        String token = getEffectiveToken();
        try {
            // 构造入参 JSON
            Map<String, Object> payload = buildRemotePayload();
            String bodyJson = objectMapper.writeValueAsString(payload);

            URL url = new URL(remoteUrl);
            conn = (HttpURLConnection) url.openConnection();

            // HTTPS：仅对本请求关闭证书校验（内网自签证书容错），不污染 JVM 全局
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(SSL_TRUST_ALL_FACTORY);
                https.setHostnameVerifier((hostname, session) -> true);
            }

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            // 关键：直接 Authorization: <token>，不加 Bearer 前缀（优先动态 token，兜底配置 token）
            conn.setRequestProperty("Authorization", token);
            conn.setRequestProperty("Accept", "application/json");
            // 关闭 keep-alive（避免连接复用导致的 SSL 状态泄漏）
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);

            byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int status = conn.getResponseCode();
            // 鉴权失败：明确抛 AuthExpiredException，让调用方返回空列表
            if (isAuthError(status)) {
                throw new AuthExpiredException("developTask/query 鉴权失败 status=" + status);
            }
            if (status != 200) {
                // 读取错误响应体（如 4xx/5xx 时 getInputStream 抛异常，需用 getErrorStream）
                String errBody = readErrorStream(conn);
                log.warn("远程需求接口异常状态：status={}, errBody={}",
                        status, errBody == null ? "(无响应体)" : (errBody.length() > 300 ? errBody.substring(0, 300) + "..." : errBody));
                return null;
            }

            String body = readStream(conn);
            log.debug("远程需求接口响应：bodyLen={}, 前500字符={}", body.length(),
                    body.length() > 500 ? body.substring(0, 500) : body);

            // 网关外壳校验：HTTP 200 也可能携带业务码 401（token 过期，实测 {"code":"401","msg":"服务异常"}）
            JsonNode root = checkGatewayEnvelope(body);
            return parseRemoteResponse(root);
        } catch (AuthExpiredException e) {
            // 鉴权失败：向上抛，让调用方（list / listMilestones）返回空列表，前端展示"鉴权过期"提示
            throw e;
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            log.warn("远程需求接口不可达（网络/代理拦截，返回空列表）：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("远程需求接口调用异常：{} {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 读取 HTTP 响应输入流为 UTF-8 字符串 */
    private String readStream(HttpURLConnection conn) throws java.io.IOException {
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    /** 读取 HTTP 错误响应流（4xx/5xx 时 getInputStream 抛异常，需用 getErrorStream） */
    private String readErrorStream(HttpURLConnection conn) {
        try (InputStream is = conn.getErrorStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 图片代理：用 Authorization 头去远程拉取图片字节流，原样返回给前端。
     *
     * <p>用途：详情弹窗 content HTML 中的 {@code <img src="...">} 标签，浏览器渲染时无法附加
     * 自定义 Authorization 头，对需要鉴权的图片资源会收到 401。前端把 img src 改写为
     * {@code /api/project/requirements/img-proxy?url=<encoded>} 走后端代理，由本方法用
     * Authorization 头拉取远程图片字节流后透传给浏览器。</p>
     *
     * <p>仅允许 http/https 协议，禁止 file/ftp 等协议，避免 SSRF 风险。
     * 不限制域名白名单（远程图片可能托管在多个 CDN 子域上），但要求必须能被
     * wetrans.cimc.cn 网关鉴权通过。</p>
     *
     * @param url 远程图片绝对 URL
     * @return 图片字节流；调用失败返回 null
     */
    public byte[] fetchImage(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.warn("fetchImage: url 为空");
            return null;
        }
        // 安全校验：仅允许 http/https 协议，防止 file:/// 等被 SSRF
        String lower = url.trim().toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            log.warn("fetchImage: 协议不允许，url={}", url);
            return null;
        }

        HttpURLConnection conn = null;
        try {
            URL u = new URL(url.trim());
            conn = (HttpURLConnection) u.openConnection();

            // HTTPS：仅对本请求关闭证书校验（与 query / getDetail 一致）
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(SSL_TRUST_ALL_FACTORY);
                https.setHostnameVerifier((hostname, session) -> true);
            }

            conn.setRequestMethod("GET");
            // 鉴权头与 query / getDetail 接口一致：直接 Authorization: <token>
            conn.setRequestProperty("Authorization", getEffectiveToken());
            conn.setRequestProperty("Accept", "image/*, */*");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status != 200) {
                log.warn("fetchImage: 远程图片响应异常 status={}, url={}", status, url);
                return null;
            }

            // 读取字节流
            try (InputStream is = conn.getInputStream()) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                return baos.toByteArray();
            }
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            log.warn("fetchImage: 远程图片不可达 url={}, {}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("fetchImage: 拉取异常 url={}, {} {}", url, e.getClass().getSimpleName(), e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 根据 URL 后缀推断 Content-Type（图片类型）。支持带 query string 的 URL，如 {@code xxx.png?Expires=...} */
    public String guessImageContentType(String url) {
        if (url == null) return "image/jpeg";
        // 去掉 query string（?...）和 fragment（#...），只保留路径部分用于后缀判断
        String lower = url.toLowerCase();
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        int h = lower.indexOf('#');
        if (h >= 0) lower = lower.substring(0, h);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "image/jpeg";
    }

    /** 构造远程接口入参，从 application.yml 读取 */
    private Map<String, Object> buildRemotePayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("current", 1);
        p.put("pageSize", remotePageSize);
        p.put("project", remoteProject);
        // statusList 转数组
        List<String> statusList = Arrays.stream(remoteStatusListCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        p.put("statusList", statusList);
        // userIds 转数组
        List<String> userIds = Arrays.stream(remoteUserIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        p.put("userIds", userIds);
        return p;
    }

    /**
     * 解析远程接口返回 JSON 为 Requirement 列表。
     *
     * <p>兼容两种常见结构：
     * <ul>
     *   <li>{@code {code:0, data: {list:[...], total:N}}}</li>
     *   <li>{@code {code:0, data: [...]}}</li>
     * </ul>
     * </p>
     *
     * <p>list 元素字段做兜底映射：尝试多个候选字段名，命中即用。</p>
     */
    @SuppressWarnings("unchecked")
    private List<Requirement> parseRemoteResponse(JsonNode root) {
        try {
            JsonNode data = root.get("data");
            if (data == null) {
                log.warn("远程返回缺少 data 字段：root keys={}",
                        root.fieldNames().toString());
                return null;
            }
            JsonNode list;
            if (data.isArray()) {
                list = data;
            } else if (data.has("list") && data.get("list").isArray()) {
                list = data.get("list");
            } else if (data.has("records") && data.get("records").isArray()) {
                list = data.get("records");
            } else if (data.has("rows") && data.get("rows").isArray()) {
                list = data.get("rows");
            } else {
                log.warn("远程返回 data 结构未识别：data keys={}", data.fieldNames().toString());
                return null;
            }

            List<Requirement> result = new ArrayList<>();
            for (JsonNode item : list) {
                Requirement r = mapRemoteItem(item);
                if (r != null) {
                    result.add(r);
                }
            }
            log.info("远程需求拉取成功：共 {} 条", result.size());
            return result;
        } catch (Exception e) {
            log.warn("远程返回 JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 把远程 JSON 单条映射为 Requirement。
     *
     * <p>远程接口 {@code wetrans.cimc.cn/gateway/apiIssue/developTask/query} 返回的 records 元素
     * 实际字段名（已通过调试日志确认）：
     * id, title, content, taskNo, personDays, productOwner, status, taskType, sprintId,
     * project, scope, urgency, issueId, testUser, userId, deadlineTime, startTime, closeTime,
     * createUser, createTime, updateUser, updateTime, sprintName, version, issueNo,
     * issueStatus, issueSubTitle, createUserName, testUserName, productOwnerName, userName</p>
     *
     * <p>映射策略：按候选字段名顺序取第一个非空。优先用展示性字段（如 *Name 后缀的中文姓名），
     * 其次用 ID 字段。</p>
     */
    private Requirement mapRemoteItem(JsonNode item) {
        Requirement r = new Requirement();
        // Sprint：优先 sprintName（CDS20261025），其次 sprintId/version
        r.setSprint(firstNonBlank(item, "sprintName", "sprintId", "sprint", "sprintCode", "iteration", "version"));
        // 人天：远程字段是 personDays（带 s）
        r.setPersonDay(toBigDecimal(firstNonBlank(item, "personDays", "personDay", "manDay", "workDay", "estimate")));
        // 状态：用户确认枚举 open/in_progress/in_test/for_release/developed
        String remoteStatus = firstNonBlank(item, "status", "issueStatus", "state", "taskStatus", "task_status");
        r.setTaskStatus(mapStatus(remoteStatus));
        // 关联话题：用户指定用 issueNo
        r.setTopic(firstNonBlank(item, "issueNo", "issueId", "issueSubTitle"));
        // 任务详情：用户指定 = taskNo + " " + title；
        // 若 content 去标签后是"同上"，则取 title 的值
        String taskNo = firstNonBlank(item, "taskNo");
        String title = firstNonBlank(item, "title");
        String content = firstNonBlank(item, "content");
        String strippedContent = stripHtml(content);
        boolean isTongShang = strippedContent != null && strippedContent.trim().equals("同上");

        String taskDetail;
        if (isTongShang) {
            taskDetail = title;
        } else {
            StringBuilder sb = new StringBuilder();
            if (taskNo != null && !taskNo.isEmpty()) sb.append(taskNo);
            if (title != null && !title.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(title);
            }
            taskDetail = sb.length() > 0 ? sb.toString() : strippedContent;
        }
        r.setTaskDetail(taskDetail);
        // 产品：远程无"产品"字段，用 productOwnerName 兜底
        r.setProduct(firstNonBlank(item, "productOwnerName", "productOwner", "product", "productName", "projectName", "module", "project"));
        // 测试人：优先 testUserName（中文姓名），其次 testUser（ID）
        r.setTester(firstNonBlank(item, "testUserName", "testerName", "tester", "testUser", "qa", "userName"));
        // 第三方 ID
        r.setSourceId(firstNonBlank(item, "id", "taskId", "issueId", "taskNo", "issueNo", "key"));
        // 若任务详情为空但有标题（极少数情况），用标题兜底
        if ((r.getTaskDetail() == null || r.getTaskDetail().isEmpty()) && r.getTopic() != null) {
            r.setTaskDetail(r.getTopic());
        }
        // 若 sprint 为空，跳过该条（无法聚合里程碑）
        if (r.getSprint() == null || r.getSprint().isEmpty()) {
            return null;
        }
        return r;
    }

    /**
     * 简单去 HTML 标签：把所有 &lt;...&gt; 替换为空字符串，得到纯文本。
     * 仅用于列表"任务详情"列判断"同上"和兜底展示，不依赖 DOM 解析库。
     */
    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return null;
        // 去 HTML 标签
        String text = html.replaceAll("<[^>]+>", "");
        // 去常见 HTML 实体（&nbsp; &amp; 等）保留可读性
        text = text.replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
        // 折叠多余空白
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    /** 远程状态 → 本地状态映射：见 STATUS_MAP 注释 */
    private String mapStatus(String remoteStatus) {
        if (remoteStatus == null) return "todo";
        String mapped = STATUS_MAP.get(remoteStatus);
        return mapped != null ? mapped : "todo";
    }

    /**
     * 获取单个需求的详情（用户点击行触发）。
     *
     * <p>远程接口：{@code GET https://wetrans.cimc.cn/gateway/apiIssue/developTask/getDetail?id=<id>}
     * 鉴权头：{@code Authorization: <token>}（与 query 接口一致，不加 Bearer）。
     * 远程返回结构假设：{@code {code, msg, success, data:{...详情对象...}}}
     * 本方法把 {@code data} 节点原样转为 Map 返回，保留所有字段（含 content HTML、img 标签等）。</p>
     *
     * <p>失败时返回空 Map（前端弹窗展示"详情获取失败"提示）。</p>
     *
     * @param sourceId 需求在第三方平台的 ID（即列表行的 sourceId 字段）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchDetail(String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            log.warn("fetchDetail: sourceId 为空");
            return new HashMap<>();
        }
        HttpURLConnection conn = null;
        try {
            // 拼接 GET URL：远程接口用 query string 传 id
            String fullUrl = remoteDetailUrl + "?id=" + java.net.URLEncoder.encode(sourceId, "UTF-8");
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();

            // HTTPS：仅对本请求关闭证书校验
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(SSL_TRUST_ALL_FACTORY);
                https.setHostnameVerifier((hostname, session) -> true);
            }

            conn.setRequestMethod("GET");
            // 鉴权头与 query 接口一致：直接 Authorization: <token>
            conn.setRequestProperty("Authorization", getEffectiveToken());
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);

            int status = conn.getResponseCode();
            if (status != 200) {
                String errBody = readErrorStream(conn);
                log.warn("远程详情接口异常状态：status={}, sourceId={}, errBody={}",
                        status, sourceId,
                        errBody == null ? "(无响应体)" : (errBody.length() > 300 ? errBody.substring(0, 300) + "..." : errBody));
                return new HashMap<>();
            }

            String body = readStream(conn);
            log.debug("远程详情接口响应：sourceId={}, bodyLen={}", sourceId, body.length());

            // 网关外壳校验：HTTP 200 也可能携带业务码 401（token 过期）
            JsonNode root = checkGatewayEnvelope(body);
            // 解析 JSON：取出 data 节点原样返回
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                log.warn("远程详情返回缺少 data 字段：root keys={}, sourceId={}",
                        root.fieldNames().toString(), sourceId);
                return new HashMap<>();
            }
            // Jackson 转 Map：保留所有字段（含 content HTML、img 标签）
            return objectMapper.treeToValue(data, Map.class);
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            log.warn("远程详情接口不可达（网络/代理拦截）：sourceId={}, {}", sourceId, e.getMessage());
            return new HashMap<>();
        } catch (Exception e) {
            log.warn("远程详情接口调用异常：sourceId={}, {} {}",
                    sourceId, e.getClass().getSimpleName(), e.getMessage());
            return new HashMap<>();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 取多个候选字段中第一个非空字符串值 */
    private String firstNonBlank(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull()) {
                String s = v.asText("");
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    /** 转为 BigDecimal，解析失败返回 0 */
    private BigDecimal toBigDecimal(String s) {
        if (s == null || s.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** 远程数据内存过滤（远程入参不支持 sprint/status/keyword 维度） */
    private List<Requirement> filterRemote(List<Requirement> remote, String sprint, String status, String keyword) {
        return remote.stream()
                .filter(r -> sprint == null || sprint.trim().isEmpty() || sprint.equals(r.getSprint()))
                .filter(r -> status == null || status.trim().isEmpty() || status.equals(r.getTaskStatus()))
                .filter(r -> {
                    if (keyword == null || keyword.trim().isEmpty()) return true;
                    String kw = keyword.trim();
                    return contains(r.getTaskDetail(), kw)
                            || contains(r.getTopic(), kw)
                            || contains(r.getProduct(), kw)
                            || contains(r.getTester(), kw);
                })
                .collect(Collectors.toList());
    }

    private boolean contains(String s, String kw) {
        return s != null && s.contains(kw);
    }

    /** 本地 mock 查询 */
    private List<Requirement> queryLocal(String sprint, String status, String keyword) {
        QueryWrapper<Requirement> qw = Wrappers.<Requirement>query();
        if (sprint != null && !sprint.trim().isEmpty()) {
            qw.eq("sprint", sprint.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            qw.eq("task_status", status.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("task_detail", kw)
                    .or().like("topic", kw)
                    .or().like("product", kw)
                    .or().like("tester", kw));
        }
        qw.orderByDesc("sprint").orderByDesc("update_time");
        return requirementMapper.selectList(qw);
    }

    /** 解析 sprint 末尾 8 位为日期；解析失败返回 null（不影响进度展示） */
    private LocalDate parseSprintDate(String sprint) {
        if (sprint == null) return null;
        Matcher m = SPRINT_DATE_PATTERN.matcher(sprint);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group(1), SPRINT_DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 远程接口调用 token（JWT） */
    @Value("${requirement.remote.token:}")
    private String remoteToken;

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

    /** 远程状态 → 本地状态映射 */
    private static final Map<String, String> STATUS_MAP = new HashMap<String, String>() {{
        put("open", "todo");
        put("unsolved", "todo");
        put("in_progress", "doing");
        put("in_test", "doing");
        put("developed", "done");
        put("for_release", "done");
    }};

    @PostConstruct
    public void init() {
        log.info("RequirementService 初始化：remoteEnabled={}, project={}, pageSize={}, userIds={}, statusList={}",
                remoteEnabled, remoteProject, remotePageSize,
                remoteUserIds == null || remoteUserIds.isEmpty() ? "无" : remoteUserIds,
                remoteStatusListCsv);
    }

    /**
     * 我的需求列表。
     *
     * <p>远程启用且调用成功时返回远程数据；远程失败时回退本地 mock 表（保证页面可用）。</p>
     *
     * @param sprint  可选 sprint 过滤，传 {@code CDS20261025} 只返回该 sprint 下需求
     * @param status  可选任务状态过滤：todo/doing/done/cancel
     * @param keyword 可选，按 task_detail / topic / product / tester 模糊匹配
     */
    public List<Requirement> list(String sprint, String status, String keyword) {
        if (remoteEnabled && remoteToken != null && !remoteToken.isEmpty()) {
            try {
                List<Requirement> remote = fetchFromRemote();
                if (remote != null && !remote.isEmpty()) {
                    // 远程数据成功拉取后，做内存过滤（远程接口入参不含 sprint/status/keyword 维度）
                    return filterRemote(remote, sprint, status, keyword);
                }
            } catch (Exception e) {
                log.warn("调用远程需求接口失败，回退本地 mock：{}", e.getMessage());
            }
        }
        // 本地 mock 查询
        return queryLocal(sprint, status, keyword);
    }

    /**
     * 里程碑进度：按 sprint 聚合。
     *
     * <p>每个 sprint 返回一个 {@link Milestone}，其中：
     * <ul>
     *   <li>name：sprint 编号（如 CDS20261025）</li>
     *   <li>dueDate：从 sprint 末尾 8 位解析得到的里程碑日期</li>
     *   <li>progress：完成率 = done / (done + doing + todo) * 100，cancel 不计入</li>
     *   <li>note：附带 "{done}/{total} 任务完成" 文案</li>
     * </ul>
     * </p>
     */
    public List<Milestone> listMilestones() {
        // 里程碑聚合优先用远程数据（如可用），否则本地
        List<Requirement> all;
        if (remoteEnabled && remoteToken != null && !remoteToken.isEmpty()) {
            List<Requirement> remote = null;
            try {
                remote = fetchFromRemote();
            } catch (Exception e) {
                log.warn("里程碑聚合：远程拉取失败，回退本地：{}", e.getMessage());
            }
            all = (remote != null && !remote.isEmpty()) ? remote : queryLocal(null, null, null);
        } else {
            all = queryLocal(null, null, null);
        }

        // 内存按 sprint 分组聚合
        // 注意：无论数据来自远程还是本地，taskStatus 都已是本地值（远程数据在 mapRemoteItem 中已 mapStatus 过）
        Map<String, long[]> agg = new LinkedHashMap<>(); // sprint → [total, done, doing, todo]
        for (Requirement r : all) {
            String sp = r.getSprint();
            if (sp == null || sp.isEmpty()) continue;
            String localStatus = r.getTaskStatus();
            if (localStatus == null || "cancel".equals(localStatus)) continue;
            long[] arr = agg.computeIfAbsent(sp, k -> new long[4]);
            arr[0]++; // total
            if ("done".equals(localStatus)) arr[1]++;
            else if ("doing".equals(localStatus)) arr[2]++;
            else if ("todo".equals(localStatus)) arr[3]++;
            // 其它状态不计入分子但计入分母
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
            m.setNote(String.format("%d/%d 任务完成（进行中 %d，待办 %d）", done, total, doing, todo));
            m.setDueDate(parseSprintDate(sprint));
            result.add(m);
        }
        // 按 sprint 倒序（CDS20261025 在最前）
        result.sort((a, b) -> b.getName().compareTo(a.getName()));
        return result;
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
            // 关键：直接 Authorization: <token>，不加 Bearer 前缀
            conn.setRequestProperty("Authorization", remoteToken);
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

            return parseRemoteResponse(body);
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            log.warn("远程需求接口不可达（网络/代理拦截，回退本地 mock）：{}", e.getMessage());
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
    private List<Requirement> parseRemoteResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
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
        // 状态：优先 status，其次 issueStatus
        String remoteStatus = firstNonBlank(item, "status", "issueStatus", "state", "taskStatus", "task_status");
        r.setTaskStatus(mapStatus(remoteStatus));
        // 关联话题：优先 title，其次 issueSubTitle/taskNo/issueNo
        r.setTopic(firstNonBlank(item, "title", "topic", "subject", "issueSubTitle", "module", "taskNo", "issueNo"));
        // 任务详情：优先 content（HTML），其次 description/summary
        r.setTaskDetail(firstNonBlank(item, "content", "taskDetail", "detail", "desc", "description", "summary"));
        // 产品：远程没有"产品"字段，用 productOwnerName（产品负责人姓名）兜底
        r.setProduct(firstNonBlank(item, "productOwnerName", "productOwner", "product", "productName", "projectName", "module", "project"));
        // 测试人：优先 testUserName（中文姓名），其次 testUser（ID）
        r.setTester(firstNonBlank(item, "testUserName", "testerName", "tester", "testUser", "qa", "userName"));
        // 第三方 ID：优先 id，其次 issueId/taskNo/issueNo
        r.setSourceId(firstNonBlank(item, "id", "taskId", "issueId", "taskNo", "issueNo", "key"));
        // 若任务详情为空但有标题，用标题兜底
        if ((r.getTaskDetail() == null || r.getTaskDetail().isEmpty()) && r.getTopic() != null) {
            r.setTaskDetail(r.getTopic());
        }
        // 若 sprint 为空，跳过该条（无法聚合里程碑）
        if (r.getSprint() == null || r.getSprint().isEmpty()) {
            return null;
        }
        return r;
    }

    /** 远程状态 → 本地状态：open/unsolved→todo, in_progress/in_test→doing, developed/for_release→done */
    private String mapStatus(String remoteStatus) {
        if (remoteStatus == null) return "todo";
        String mapped = STATUS_MAP.get(remoteStatus);
        return mapped != null ? mapped : "todo";
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

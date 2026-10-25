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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
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

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** sprint 中 8 位日期的解析格式 yyyyMMdd */
    private static final DateTimeFormatter SPRINT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 匹配 sprint 末尾 8 位数字（CDS20261025 -> 20261025） */
    private static final Pattern SPRINT_DATE_PATTERN = Pattern.compile("(\\d{8})$");

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
        this.restTemplate = new RestTemplate();
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
     * <p>Header: Authorization: Bearer <token>, Content-Type: application/json
     * Body: 由 application.yml 的 project/pageSize/userIds/statusList 配置构造</p>
     *
     * <p>返回解析假设：JSON 结构为 {@code {code, data: {list:[...], total:N}}}
     * 或 {@code {code, data: [...]}}，兼容两种；list 元素字段名做常见映射：
     * sprint/sprintName/sprintCode, personDay/manDay/workDay,
     * status/state, topic/title, taskDetail/detail/desc/description,
     * product/productName, tester/testerName/testUser</p>
     *
     * @return 解析后的需求列表；调用失败返回 null 由调用方回退本地
     */
    private List<Requirement> fetchFromRemote() {
        try {
            // 构造入参
            Map<String, Object> payload = buildRemotePayload();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(remoteToken);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(remoteUrl, entity, String.class);

            if (resp.getStatusCodeValue() != 200 || resp.getBody() == null) {
                log.warn("远程需求接口异常状态：status={}", resp.getStatusCodeValue());
                return null;
            }

            String body = resp.getBody();
            log.debug("远程需求接口响应：bodyLen={}, 前500字符={}", body.length(),
                    body.length() > 500 ? body.substring(0, 500) : body);

            return parseRemoteResponse(body);
        } catch (Exception e) {
            log.warn("远程需求接口调用异常：{}", e.getMessage());
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

    /** 把远程 JSON 单条映射为 Requirement；尝试多个候选字段名兜底 */
    private Requirement mapRemoteItem(JsonNode item) {
        Requirement r = new Requirement();
        r.setSprint(firstNonBlank(item, "sprint", "sprintName", "sprintCode", "iteration"));
        r.setPersonDay(toBigDecimal(firstNonBlank(item, "personDay", "manDay", "workDay", "estimate")));
        String remoteStatus = firstNonBlank(item, "status", "state", "taskStatus", "task_status");
        r.setTaskStatus(mapStatus(remoteStatus));
        r.setTopic(firstNonBlank(item, "topic", "title", "subject", "module"));
        r.setTaskDetail(firstNonBlank(item, "taskDetail", "detail", "desc", "description", "content", "summary"));
        r.setProduct(firstNonBlank(item, "product", "productName", "projectName", "module"));
        r.setTester(firstNonBlank(item, "tester", "testerName", "testUser", "qa"));
        r.setSourceId(firstNonBlank(item, "id", "taskId", "issueId", "key"));
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

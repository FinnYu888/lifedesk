package com.lifedesk.extmonitor.rocketmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RocketMQ Dashboard（rocketmq-console-ng）数据采集服务。
 *
 * <p>通过登录 Dashboard 会话后依次调用 {@code cluster/list.query}、{@code topic/list.query}、
 * {@code consumer/groupList.query} 抓取集群实时指标，聚合为一张总览视图。
 * 自签证书走全信任 SSL；会话 Cookie 失效自动重登；聚合结果内存缓存 30 秒。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
public class RocketMqMonitorService {

    @Value("${ext-monitor.rocketmq.dashboard-url:}")
    private String dashboardUrl;

    @Value("${ext-monitor.rocketmq.username:}")
    private String username;

    @Value("${ext-monitor.rocketmq.password:}")
    private String password;

    @Value("${ext-monitor.rocketmq.cache-ttl-seconds:30}")
    private long cacheTtlSeconds;

    private final ObjectMapper mapper = new ObjectMapper();

    /** 会话 Cookie（JSESSIONID=...） */
    private volatile String sessionCookie;
    /** 聚合缓存 */
    private volatile Map<String, Object> cached;
    private volatile long cachedAt;

    @PostConstruct
    void initSslTrust() {
        // Dashboard 为自签 HTTPS，仅对采集连接放宽校验（独立 factory，不影响系统其他 HTTPS）
        try {
            TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((HostnameVerifier) (hostname, session) -> true);
            log.info("RocketMQ monitor: trust-all SSL initialized");
        } catch (Exception e) {
            log.warn("RocketMQ monitor: init SSL failed", e);
        }
    }

    /** 获取 RocketMQ 集群总览（带缓存） */
    public Map<String, Object> overview() {
        long now = System.currentTimeMillis();
        Map<String, Object> hit = cached;
        if (hit != null && now - cachedAt < cacheTtlSeconds * 1000) {
            return hit;
        }
        synchronized (this) {
            if (cached != null && System.currentTimeMillis() - cachedAt < cacheTtlSeconds * 1000) {
                return cached;
            }
            Map<String, Object> data = collect();
            cached = data;
            cachedAt = System.currentTimeMillis();
            return data;
        }
    }

    /** 强制刷新（绕过缓存） */
    public Map<String, Object> refresh() {
        synchronized (this) {
            sessionCookie = null;
            Map<String, Object> data = collect();
            cached = data;
            cachedAt = System.currentTimeMillis();
            return data;
        }
    }

    // ==================== 采集与聚合 ====================

    private Map<String, Object> collect() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            JsonNode cluster = getJson("cluster/list.query");
            JsonNode topicList = getJson("topic/list.query");
            JsonNode groupList = getJson("consumer/groupList.query");
            result.putAll(buildOverview(cluster, topicList, groupList));
            result.put("fetchedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            result.put("ok", true);
        } catch (Exception e) {
            log.warn("RocketMQ monitor collect failed: {}", e.getMessage());
            result.put("ok", false);
            result.put("errMsg", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> buildOverview(JsonNode cluster, JsonNode topicNode, JsonNode groupNode) {
        Map<String, Object> overview = new LinkedHashMap<>();
        List<Map<String, Object>> brokers = new ArrayList<>();

        JsonNode brokerServer = cluster.path("data").path("brokerServer");
        long todayPut = 0, todayGet = 0;
        double maxDiskRatio = 0;
        boolean allActive = true;
        int nodeCount = 0;

        if (brokerServer.isObject()) {
            // 结构：brokerServer -> { brokerName -> { brokerId -> 节点数据 } }，名称/ID 都在键上
            for (Iterator<Map.Entry<String, JsonNode>> git = brokerServer.fields(); git.hasNext(); ) {
                Map.Entry<String, JsonNode> group = git.next();
                String brokerName = group.getKey();
                JsonNode nodes = group.getValue();
                for (Iterator<Map.Entry<String, JsonNode>> nit = nodes.fields(); nit.hasNext(); ) {
                    Map.Entry<String, JsonNode> entry = nit.next();
                    String brokerIdStr = entry.getKey();
                    JsonNode node = entry.getValue();
                    nodeCount++;
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("name", brokerName);
                    b.put("brokerId", parseBrokerId(brokerIdStr));
                    boolean active = node.path("brokerActive").asBoolean(false);
                    int brokerId = parseBrokerId(brokerIdStr);
                    b.put("active", active);
                    b.put("master", brokerId == 0);
                    if (brokerId == 0 && !active) {
                        allActive = false; // 健康以 master 为准（slave 在旧版控制台常误报 inactive）
                    }
                    b.put("version", text(node, "brokerVersionDesc"));
                    b.put("putTps", round2(firstTps(node, "putTps")));
                    b.put("getFoundTps", round2(firstTps(node, "getFoundTps")));
                    long put = node.path("msgPutTotalTodayNow").asLong(0);
                    long get = node.path("msgGetTotalTodayNow").asLong(0);
                    b.put("todayPut", put);
                    b.put("todayGet", get);
                    todayPut += put;
                    todayGet += get;
                    double disk = node.path("commitLogDiskRatio").asDouble(0);
                    b.put("diskRatio", disk);
                    if (disk > maxDiskRatio) maxDiskRatio = disk;
                    b.put("diskCapacity", text(node, "commitLogDirCapacity"));
                    b.put("putMaxLatency", node.path("putMessageEntireTimeMax").asDouble(0));
                    b.put("putLatency99", node.path("putLatency99").asDouble(0));
                    b.put("sendQueueSize", node.path("sendThreadPoolQueueSize").asInt(0));
                    b.put("dispatchBehindBytes", node.path("dispatchBehindBytes").asLong(0));
                    b.put("runtime", cleanRuntime(text(node, "runtime")));
                    b.put("bootTime", fmtTime(node.path("bootTimestamp").asLong(0)));
                    brokers.add(b);
                }
            }
        }
        brokers.sort((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))));

        // Topic 统计
        JsonNode topics = topicNode.path("data").path("topicList");
        int topicTotal = topics.isArray() ? topics.size() : 0;
        int bizTopics = 0;
        if (topics.isArray()) {
            for (JsonNode t : topics) {
                String name = t.asText();
                if (!name.startsWith("%")) bizTopics++;
            }
        }

        // 消费组统计 + 积压 Top
        JsonNode groups = groupNode.path("data");
        int groupTotal = groups.isArray() ? groups.size() : 0;
        int onlineGroups = 0;
        long totalLag = 0;
        double totalConsumeTps = 0;
        List<Map<String, Object>> topLag = new ArrayList<>();
        if (groups.isArray()) {
            for (JsonNode g : groups) {
                long diff = g.path("diffTotal").asLong(0);
                int consumerCount = g.path("count").asInt(0);
                if (consumerCount > 0) onlineGroups++;
                if (diff > 0) {
                    totalLag += diff;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("group", g.path("group").asText());
                    item.put("consumerCount", consumerCount);
                    item.put("consumeTps", g.path("consumeTps").asDouble(0));
                    item.put("lag", diff);
                    topLag.add(item);
                }
                totalConsumeTps += g.path("consumeTps").asDouble(0);
            }
        }
        topLag.sort((a, b) -> Long.compare((long) b.get("lag"), (long) a.get("lag")));
        if (topLag.size() > 8) topLag = new ArrayList<>(topLag.subList(0, 8));

        Map<String, Object> clusterInfo = new LinkedHashMap<>();
        clusterInfo.put("allActive", allActive);
        clusterInfo.put("brokerNodeCount", nodeCount);
        clusterInfo.put("topicTotal", topicTotal);
        clusterInfo.put("bizTopicTotal", bizTopics);
        clusterInfo.put("groupTotal", groupTotal);
        clusterInfo.put("onlineGroups", onlineGroups);
        clusterInfo.put("todayPut", todayPut);
        clusterInfo.put("todayGet", todayGet);
        clusterInfo.put("maxDiskRatio", maxDiskRatio);
        clusterInfo.put("totalConsumeTps", Math.round(totalConsumeTps * 100) / 100.0);
        clusterInfo.put("totalLag", totalLag);

        overview.put("cluster", clusterInfo);
        overview.put("brokers", brokers);
        overview.put("topLag", topLag);
        return overview;
    }

    // ==================== HTTP 采集底层 ====================

    /** 带 JSON 解析的 GET，status 异常时重登一次再取 */
    private JsonNode getJson(String path) throws Exception {
        JsonNode node = mapper.readTree(httpGet(path));
        if (node.path("status").asInt(-1) != 0) {
            if (login()) {
                node = mapper.readTree(httpGet(path));
            }
        }
        if (node.path("status").asInt(-1) != 0) {
            throw new IllegalStateException("Dashboard 响应异常: status=" + node.path("status").asInt(-1)
                    + " errMsg=" + node.path("errMsg").asText(""));
        }
        return node;
    }

    private String httpGet(String path) throws Exception {
        HttpURLConnection conn = open(path);
        try {
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            String body = readBody(conn);
            if (code == 401 || code == 403 || body.trim().startsWith("<") || body.isEmpty()) {
                if (login()) {
                    conn = open(path);
                    conn.setRequestMethod("GET");
                    code = conn.getResponseCode();
                    body = readBody(conn);
                }
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }

    /** 登录 Dashboard，成功后保存会话 Cookie */
    private boolean login() throws Exception {
        String form = "username=" + URLEncoder.encode(username, "UTF-8")
                + "&password=" + URLEncoder.encode(password, "UTF-8");
        HttpURLConnection conn = open("login/login.do");
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(form.getBytes(StandardCharsets.UTF_8).length);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(form.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String body = readBody(conn);
            String cookie = conn.getHeaderField("Set-Cookie");
            if (code == 200 && body.contains("\"status\":0") && cookie != null) {
                sessionCookie = cookie.split(";")[0].trim();
                log.info("RocketMQ monitor: dashboard login ok");
                return true;
            }
            log.warn("RocketMQ monitor: login failed code={} body={}", code, body);
            return false;
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection open(String path) throws Exception {
        String base = dashboardUrl.endsWith("/") ? dashboardUrl : dashboardUrl + "/";
        HttpURLConnection conn = (HttpURLConnection) new URL(base + path).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "LifeDesk-Monitor/1.0");
        String cookie = sessionCookie;
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        InputStream is;
        try {
            is = conn.getInputStream();
        } catch (Exception e) {
            is = conn.getErrorStream();
        }
        if (is == null) {
            return "";
        }
        try (InputStream in = is) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ==================== 小工具 ====================

    private int parseBrokerId(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** putTps 形如 "24.79 24.62 24.06"，取第一个（最近 10s） */
    private double firstTps(JsonNode node, String field) {
        String raw = node.path(field).asText("");
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(raw.trim().split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String text(JsonNode node, String field) {
        String v = node.path(field).asText("");
        return v.isEmpty() ? null : v;
    }

    private String cleanRuntime(String runtime) {
        if (runtime == null) {
            return null;
        }
        return runtime.replace("[", "").replace("]", "").trim();
    }

    private String fmtTime(long ts) {
        if (ts <= 0) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(ts));
    }
}

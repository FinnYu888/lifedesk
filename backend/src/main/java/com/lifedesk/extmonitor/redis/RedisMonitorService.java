package com.lifedesk.extmonitor.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 监控采集服务（轻量 RESP 客户端，无第三方依赖）。
 *
 * <p>短连接执行 AUTH + INFO，解析 Server / Clients / Memory / Stats / Replication / Keyspace
 * 各段指标；只输出配置的 database（默认 0 和 1）的键空间指标。结果内存缓存 30 秒。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
public class RedisMonitorService {

    @Value("${ext-monitor.redis.host:}")
    private String host;

    @Value("${ext-monitor.redis.port:6379}")
    private int port;

    @Value("${ext-monitor.redis.password:}")
    private String password;

    /** 只展示的 database 列表，逗号分隔 */
    @Value("${ext-monitor.redis.databases:0,1}")
    private String databases;

    @Value("${ext-monitor.redis.cache-ttl-seconds:30}")
    private long cacheTtlSeconds;

    private List<Integer> dbList;

    private volatile Map<String, Object> cached;
    private volatile long cachedAt;

    // ==================== 24h 趋势采样（内存滚动窗口） ====================

    /** 采样间隔 30s × 24h = 2880 点滚动窗口 */
    private static final int TREND_MAX_POINTS = 2880;

    private final java.util.Deque<Map<String, Object>> trendHistory = new java.util.concurrent.ConcurrentLinkedDeque<>();

    /**
     * 记录一个趋势采样点（由告警评估器每 30s 调用，采集失败时跳过）。
     * 点结构：{time, ops, usedMb, hitRate, clients}
     */
    public void recordTrendPoint(Map<String, Object> overview) {
        if (!Boolean.TRUE.equals(overview.get("ok"))) {
            return;
        }
        try {
            Map<?, ?> stats = (Map<?, ?>) overview.get("stats");
            Map<?, ?> memory = (Map<?, ?>) overview.get("memory");
            Map<?, ?> clients = (Map<?, ?>) overview.get("clients");
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", new SimpleDateFormat("HH:mm:ss").format(new Date()));
            point.put("ops", stats != null && stats.get("opsPerSec") != null ? ((Number) stats.get("opsPerSec")).longValue() : 0L);
            long usedBytes = memory != null && memory.get("usedBytes") != null ? ((Number) memory.get("usedBytes")).longValue() : 0L;
            point.put("usedMb", Math.round(usedBytes / 1048576.0 * 10) / 10.0);
            point.put("hitRate", stats != null && stats.get("hitRate") != null ? ((Number) stats.get("hitRate")).doubleValue() : 0.0);
            point.put("clients", clients != null && clients.get("connected") != null ? ((Number) clients.get("connected")).longValue() : 0L);
            trendHistory.addLast(point);
            while (trendHistory.size() > TREND_MAX_POINTS) {
                trendHistory.pollFirst();
            }
        } catch (Exception e) {
            log.warn("Redis trend record failed: {}", e.getMessage());
        }
    }

    /** 24h 趋势采样序列（按时间升序） */
    public List<Map<String, Object>> trend() {
        return new ArrayList<>(trendHistory);
    }

    @PostConstruct
    void init() {
        dbList = new ArrayList<>();
        for (String s : databases.split(",")) {
            try {
                dbList.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignore) {
            }
        }
        log.info("Redis monitor: target {}:{}, databases={}", host, port, dbList);
    }

    /** Redis 指标总览（带缓存） */
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

    /** 强制刷新 */
    public Map<String, Object> refresh() {
        synchronized (this) {
            Map<String, Object> data = collect();
            cached = data;
            cachedAt = System.currentTimeMillis();
            return data;
        }
    }

    // ==================== 采集与聚合 ====================

    private Map<String, Object> collect() {
        Map<String, Object> result = new LinkedHashMap<>();
        try (RespConnection conn = new RespConnection(host, port)) {
            conn.connect();
            Object authReply = conn.command("AUTH", password);
            if (authReply instanceof byte[] && new String((byte[]) authReply, StandardCharsets.UTF_8).startsWith("ERR")) {
                throw new IllegalStateException("Redis 认证失败");
            }
            String info = bulkString(conn.command("INFO"));
            result.putAll(parseInfo(info));
            result.put("fetchedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            result.put("ok", true);
        } catch (Exception e) {
            log.warn("Redis monitor collect failed: {}", e.getMessage());
            result.put("ok", false);
            result.put("errMsg", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> parseInfo(String info) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        String cur = null;
        for (String line : info.split("\r\n|\n")) {
            if (line.startsWith("#")) {
                cur = line.substring(1).trim();
                sections.put(cur, new LinkedHashMap<>());
            } else if (cur != null && line.contains(":")) {
                int idx = line.indexOf(':');
                sections.get(cur).put(line.substring(0, idx), line.substring(idx + 1));
            }
        }

        Map<String, String> server = sections.getOrDefault("Server", new LinkedHashMap<>());
        Map<String, String> clients = sections.getOrDefault("Clients", new LinkedHashMap<>());
        Map<String, String> memory = sections.getOrDefault("Memory", new LinkedHashMap<>());
        Map<String, String> stats = sections.getOrDefault("Stats", new LinkedHashMap<>());
        Map<String, String> repl = sections.getOrDefault("Replication", new LinkedHashMap<>());
        Map<String, String> keyspace = sections.getOrDefault("Keyspace", new LinkedHashMap<>());

        Map<String, Object> r = new LinkedHashMap<>();

        // 服务器信息
        Map<String, Object> srv = new LinkedHashMap<>();
        srv.put("version", server.get("redis_version"));
        srv.put("uptimeDays", longVal(server.get("uptime_in_days")));
        srv.put("role", "master".equals(repl.get("role")) ? "主节点" : ("slave".equals(repl.get("role")) ? "从节点" : "—"));
        r.put("server", srv);

        // 客户端
        Map<String, Object> cli = new LinkedHashMap<>();
        cli.put("connected", longVal(clients.get("connected_clients")));
        cli.put("maxClients", longVal(clients.get("maxclients")));
        cli.put("blocked", longVal(clients.get("blocked_clients")));
        r.put("clients", cli);

        // 内存
        Map<String, Object> mem = new LinkedHashMap<>();
        String used = memory.get("used_memory_human");
        mem.put("used", used);
        mem.put("usedBytes", longVal(memory.get("used_memory")));
        mem.put("peak", memory.get("used_memory_peak_human"));
        String maxMem = memory.get("maxmemory");
        mem.put("max", (maxMem == null || "0".equals(maxMem)) ? "不限制" : memory.get("maxmemory_human"));
        mem.put("fragRatio", doubleVal(memory.get("mem_fragmentation_ratio")));
        r.put("memory", mem);

        // 统计
        long hits = longVal(stats.get("keyspace_hits"));
        long misses = longVal(stats.get("keyspace_misses"));
        long totalLookups = hits + misses;
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("opsPerSec", longVal(stats.get("instantaneous_ops_per_sec")));
        st.put("totalCommands", longVal(stats.get("total_commands_processed")));
        st.put("hits", hits);
        st.put("misses", misses);
        st.put("hitRate", totalLookups > 0 ? Math.round(hits * 1000.0 / totalLookups) / 10.0 : 0);
        st.put("rejectedConnections", longVal(stats.get("rejected_connections")));
        st.put("evictedKeys", longVal(stats.get("evicted_keys")));
        st.put("expiredKeys", longVal(stats.get("expired_keys")));
        r.put("stats", st);

        // 指定 database 的键空间指标
        List<Map<String, Object>> dbs = new ArrayList<>();
        long totalKeys = 0;
        for (Integer db : dbList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("db", db);
            String raw = keyspace.get("db" + db); // keys=3188,expires=1548,avg_ttl=1059705596
            if (raw == null) {
                item.put("keys", 0L);
                item.put("expires", 0L);
                item.put("avgTtl", null);
            } else {
                Map<String, String> kv = new LinkedHashMap<>();
                for (String part : raw.split(",")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        kv.put(part.substring(0, eq), part.substring(eq + 1));
                    }
                }
                long keys = longVal(kv.get("keys"));
                totalKeys += keys;
                item.put("keys", keys);
                item.put("expires", longVal(kv.get("expires")));
                item.put("avgTtl", humanizeTtl(longVal(kv.get("avg_ttl"))));
            }
            dbs.add(item);
        }
        r.put("databases", dbs);
        r.put("totalKeys", totalKeys);
        return r;
    }

    // ==================== 轻量 RESP 客户端 ====================

    /** 单连接 RESP：connect → 可多次 command → close */
    private static class RespConnection implements AutoCloseable {
        private Socket socket;
        private BufferedReader reader;
        private OutputStream out;

        RespConnection(String host, int port) {
            this.host = host;
            this.port = port;
        }

        private final String host;
        private final int port;

        void connect() throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 8000);
            socket.setSoTimeout(15000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = socket.getOutputStream();
        }

        /** 执行命令，返回简单字符串(String)/错误(String)/整数(Long)/批量字符串(byte[]) */
        Object command(String... args) throws IOException {
            StringBuilder sb = new StringBuilder("*" + args.length + "\r\n");
            for (String a : args) {
                sb.append("$").append(a.getBytes(StandardCharsets.UTF_8).length).append("\r\n").append(a).append("\r\n");
            }
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readReply();
        }

        private Object readReply() throws IOException {
            String typeLine = reader.readLine();
            if (typeLine == null) {
                throw new IOException("连接已关闭");
            }
            char type = typeLine.charAt(0);
            String body = typeLine.substring(1);
            switch (type) {
                case '+': // simple string
                case '-': // error
                    return body;
                case ':': // integer
                    return Long.parseLong(body);
                case '$': // bulk string
                    int len = Integer.parseInt(body);
                    if (len == -1) {
                        return null;
                    }
                    char[] buf = new char[len];
                    int read = 0;
                    while (read < len) {
                        int n = reader.read(buf, read, len - read);
                        if (n < 0) {
                            throw new IOException("数据流提前结束");
                        }
                        read += n;
                    }
                    reader.read(new char[2]); // 消费 CRLF
                    // 以字节保真转回（INFO 内容均为 ASCII/Latin 安全）
                    return new String(new String(buf).getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1).getBytes(StandardCharsets.ISO_8859_1);
                default:
                    throw new IOException("不支持的 RESP 类型: " + type);
            }
        }

        @Override
        public void close() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    // ==================== 小工具 ====================

    private static String bulkString(Object reply) {
        if (reply instanceof byte[]) {
            return new String((byte[]) reply, StandardCharsets.UTF_8);
        }
        return reply == null ? "" : String.valueOf(reply);
    }

    private static long longVal(String v) {
        if (v == null || v.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return doubleVal(v).longValue();
        }
    }

    private static Double doubleVal(String v) {
        if (v == null || v.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** avg_ttl（ms）转可读时长 */
    private static String humanizeTtl(long ms) {
        if (ms <= 0) {
            return null;
        }
        long days = ms / 86400000;
        long hours = (ms % 86400000) / 3600000;
        if (days > 0) {
            return days + "天" + (hours > 0 ? hours + "小时" : "");
        }
        long minutes = (ms % 3600000) / 60000;
        if (hours > 0) {
            return hours + "小时" + (minutes > 0 ? minutes + "分" : "");
        }
        return Math.max(1, minutes) + "分钟";
    }
}

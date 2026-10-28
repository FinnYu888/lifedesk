package com.lifedesk.extmonitor.mysql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL 监控采集服务。
 *
 * <p>对配置的每个 database 建立独立连接：验证连通性（带延迟测量）、统计表数量与数据大小
 * （information_schema）；并在首个成功连接上抓取全局状态（连接数/活跃线程/QPS/最大连接等）。
 * 结果内存缓存 30 秒。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
public class MysqlMonitorService {

    @Value("${ext-monitor.mysql.host:}")
    private String host;

    @Value("${ext-monitor.mysql.port:3306}")
    private int port;

    @Value("${ext-monitor.mysql.username:}")
    private String username;

    @Value("${ext-monitor.mysql.password:}")
    private String password;

    @Value("${ext-monitor.mysql.databases:}")
    private String databases;

    @Value("${ext-monitor.mysql.cache-ttl-seconds:30}")
    private long cacheTtlSeconds;

    private List<String> dbList;

    private volatile Map<String, Object> cached;
    private volatile long cachedAt;

    @PostConstruct
    void init() {
        dbList = new ArrayList<>();
        for (String s : databases.split(",")) {
            if (!s.trim().isEmpty()) {
                dbList.add(s.trim());
            }
        }
        log.info("MySQL monitor: target {}:{}, databases={}", host, port, dbList);
    }

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

    public Map<String, Object> refresh() {
        synchronized (this) {
            Map<String, Object> data = collect();
            cached = data;
            cachedAt = System.currentTimeMillis();
            return data;
        }
    }

    // ==================== 采集 ====================

    private Map<String, Object> collect() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> dbs = new ArrayList<>();
        int upCount = 0;
        long totalSize = 0;
        long totalTables = 0;
        Map<String, String> global = null;
        String version = null;

        for (String db : dbList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("db", db);
            long t0 = System.currentTimeMillis();
            try (Connection conn = DriverManager.getConnection(jdbcUrl(db), username, password)) {
                item.put("ok", true);
                item.put("latencyMs", System.currentTimeMillis() - t0);
                if (version == null) {
                    version = scalar(conn, "SELECT VERSION()");
                }
                // 库级：表数量与数据大小
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) AS tbl, COALESCE(SUM(data_length+index_length),0) AS size, "
                                + "COALESCE(SUM(data_free),0) AS free FROM information_schema.tables WHERE table_schema = ?")) {
                    ps.setString(1, db);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            item.put("tables", rs.getLong(1));
                            item.put("sizeBytes", rs.getLong(2));
                            item.put("freeBytes", rs.getLong(3));
                        }
                    }
                }
                if (global == null) {
                    global = fetchGlobalStatus(conn);
                }
                upCount++;
            } catch (Exception e) {
                item.put("ok", false);
                item.put("errMsg", e.getMessage());
                log.warn("MySQL monitor: {} connect failed: {}", db, e.getMessage());
            }
            dbs.add(item);
        }

        for (Map<String, Object> d : dbs) {
            if (Boolean.TRUE.equals(d.get("ok"))) {
                totalSize += (long) d.getOrDefault("sizeBytes", 0L);
                totalTables += (long) d.getOrDefault("tables", 0L);
            }
        }

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("version", version);
        if (global != null) {
            long uptime = parseLong(global.get("Uptime"));
            server.put("uptimeDays", uptime / 86400);
            server.put("avgQps", uptime > 0 ? Math.round(parseLong(global.get("Questions")) * 10.0 / uptime) / 10.0 : 0);
        }

        Map<String, Object> g = new LinkedHashMap<>();
        if (global != null) {
            g.put("threadsConnected", parseLong(global.get("Threads_connected")));
            g.put("threadsRunning", parseLong(global.get("Threads_running")));
            g.put("maxUsedConnections", parseLong(global.get("Max_used_connections")));
            g.put("abortedConnects", parseLong(global.get("Aborted_connects")));
        }
        // max_connections 取任一可用连接的变量
        try (Connection conn = openAny()) {
            if (conn != null) {
                g.put("maxConnections", parseLong(scalar(conn, "SELECT @@max_connections")));
            }
        } catch (Exception ignore) {
        }

        result.put("server", server);
        result.put("global", g);
        result.put("databases", dbs);
        result.put("dbTotal", dbList.size());
        result.put("dbUp", upCount);
        result.put("totalSizeBytes", totalSize);
        result.put("totalTables", totalTables);
        result.put("allUp", upCount == dbList.size());
        result.put("fetchedAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        result.put("ok", upCount > 0);
        return result;
    }

    /** 抓取 SHOW GLOBAL STATUS 关键项 */
    private Map<String, String> fetchGlobalStatus(Connection conn) throws Exception {
        Map<String, String> m = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS WHERE Variable_name IN "
                     + "('Threads_connected','Threads_running','Questions','Uptime','Max_used_connections','Aborted_connects')")) {
            while (rs.next()) {
                m.put(rs.getString(1), rs.getString(2));
            }
        }
        return m;
    }

    private Connection openAny() throws Exception {
        for (String db : dbList) {
            try {
                return DriverManager.getConnection(jdbcUrl(db), username, password);
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private String scalar(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? String.valueOf(rs.getObject(1)) : null;
        }
    }

    private String jdbcUrl(String db) {
        return "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8"
                + "&connectTimeout=8000&socketTimeout=15000&allowPublicKeyRetrieval=true";
    }

    private long parseLong(String v) {
        if (v == null || v.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

package com.lifedesk.extmonitor.oss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * S3 兼容对象存储（OSS）监控采集服务。
 *
 * <p>手写 AWS Signature V4 签名访问 S3 兼容端点（无 SDK 依赖）：HEAD 桶测可达性与延迟，
 * ListObjectsV1 分页全量统计对象数与总容量。桶内对象量大时全量遍历耗时数分钟，
 * 因此采用「后台单线程异步统计 + 长缓存（默认 1 小时）」：overview() 立即返回最近结果，
 * 首次或过期时仅触发后台重算（collecting=true 表示统计进行中）。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
public class OssMonitorService {

    @Value("${ext-monitor.oss.endpoint:}")
    private String endpoint;

    @Value("${ext-monitor.oss.access-key:}")
    private String accessKey;

    @Value("${ext-monitor.oss.secret-key:}")
    private String secretKey;

    @Value("${ext-monitor.oss.buckets:}")
    private String buckets;

    /** 全量统计很耗时，缓存默认 1 小时 */
    @Value("${ext-monitor.oss.cache-ttl-seconds:3600}")
    private long cacheTtlSeconds;

    private List<String> bucketList;

    private volatile Map<String, Object> cached;
    private volatile long cachedAt;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "oss-monitor");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void init() {
        bucketList = new ArrayList<>();
        for (String s : buckets.split(",")) {
            if (!s.trim().isEmpty()) {
                bucketList.add(s.trim());
            }
        }
        initSslTrust();
        log.info("OSS monitor: endpoint={}, buckets={}", endpoint, bucketList);
    }

    private void initSslTrust() {
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
        } catch (Exception e) {
            log.warn("OSS monitor: init SSL failed", e);
        }
    }

    /** 立即返回最近统计结果；无结果或已过期则触发后台重算 */
    public Map<String, Object> overview() {
        Map<String, Object> hit = cached;
        boolean stale = hit == null || System.currentTimeMillis() - cachedAt >= cacheTtlSeconds * 1000;
        if (stale) {
            triggerRefresh();
        }
        if (hit != null) {
            // 附带统计中标记，前端可提示
            hit.put("collecting", refreshing.get());
            return hit;
        }
        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("ok", true);
        placeholder.put("collecting", true);
        placeholder.put("endpoint", endpoint);
        placeholder.put("bucketTotal", bucketList.size());
        placeholder.put("buckets", new ArrayList<>());
        placeholder.put("errMsg", null);
        return placeholder;
    }

    /** 触发后台重新统计，返回当前已有结果 */
    public Map<String, Object> refresh() {
        triggerRefresh();
        Map<String, Object> hit = cached;
        if (hit != null) {
            hit.put("collecting", refreshing.get());
            return hit;
        }
        return overview();
    }

    private void triggerRefresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        worker.submit(() -> {
            try {
                Map<String, Object> data = collect();
                if (Boolean.TRUE.equals(data.get("ok"))) {
                    cached = data;
                    cachedAt = System.currentTimeMillis();
                } else {
                    // 采集失败也记录（若此前无结果，让前端能看到错误）
                    if (cached == null) {
                        cached = data;
                        cachedAt = System.currentTimeMillis();
                    }
                    log.warn("OSS monitor: collect failed: {}", data.get("errMsg"));
                }
            } catch (Exception e) {
                log.warn("OSS monitor: collect error", e);
            } finally {
                refreshing.set(false);
            }
        });
    }

    // ==================== 采集 ====================

    private Map<String, Object> collect() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> bucketsOut = new ArrayList<>();
        long totalObjects = 0;
        long totalSize = 0;
        int okBuckets = 0;
        try {
            for (String bucket : bucketList) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("name", bucket);
                // HEAD 桶：可达性 + 延迟
                long t0 = System.currentTimeMillis();
                int headCode = headBucket(bucket);
                b.put("latencyMs", System.currentTimeMillis() - t0);
                b.put("accessible", headCode == 200);
                if (headCode == 200) {
                    long[] stat = scanBucket(bucket); // [objects, sizeBytes]
                    b.put("objects", stat[0]);
                    b.put("sizeBytes", stat[1]);
                    b.put("ok", true);
                    totalObjects += stat[0];
                    totalSize += stat[1];
                    okBuckets++;
                } else {
                    b.put("ok", false);
                    b.put("errMsg", "HTTP " + headCode);
                }
                bucketsOut.add(b);
            }
            result.put("ok", okBuckets > 0);
            result.put("errMsg", okBuckets == 0 ? "所有桶均不可访问" : null);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("errMsg", e.getMessage());
        }
        result.put("endpoint", endpoint);
        result.put("buckets", bucketsOut);
        result.put("bucketTotal", bucketList.size());
        result.put("bucketOk", okBuckets);
        result.put("totalObjects", totalObjects);
        result.put("totalSizeBytes", totalSize);
        result.put("fetchedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        return result;
    }

    /** 全量分页统计：返回 [对象数, 总字节] */
    private long[] scanBucket(String bucket) throws Exception {
        long objects = 0;
        long size = 0;
        String marker = null;
        int pages = 0;
        while (pages++ < 2000) { // 安全上限：200 万对象
            TreeMap<String, String> params = new TreeMap<>();
            params.put("max-keys", "1000");
            if (marker != null) {
                params.put("marker", marker);
            }
            String xml = request("GET", "/" + bucket, params);
            List<String> keys = regexAll(xml, "<Key>(.*?)</Key>");
            List<String> sizes = regexAll(xml, "<Size>(\\d+)</Size>");
            objects += keys.size();
            for (String s : sizes) {
                size += Long.parseLong(s);
            }
            if (xml.contains("<IsTruncated>true</IsTruncated>") && !keys.isEmpty()) {
                marker = xmlUnescape(keys.get(keys.size() - 1));
            } else {
                break;
            }
        }
        return new long[]{objects, size};
    }

    /** 桶存活探测：GET /bucket?max-keys=1（HEAD 在该端点 403 且无错误体，GET 可拿到详情） */
    private int headBucket(String bucket) {
        HttpURLConnection conn = null;
        try {
            TreeMap<String, String> params = new TreeMap<>();
            params.put("max-keys", "1");
            conn = signed("GET", "/" + bucket, params);
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("OSS monitor: GET /{} -> {} body={}", bucket, code, readBody(conn));
            }
            return code;
        } catch (Exception e) {
            log.warn("OSS monitor: GET /{} failed: {}", bucket, e.toString());
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ==================== S3 REST + AWS SigV4 ====================

    /** GET 请求返回响应体文本 */
    private String request(String method, String path, TreeMap<String, String> params) throws Exception {
        HttpURLConnection conn = signed(method, path, params);
        int code = conn.getResponseCode();
        String body = readBody(conn);
        conn.disconnect();
        if (code != 200) {
            throw new IllegalStateException("S3 " + method + " " + path + " -> " + code + " " + body);
        }
        return body;
    }

    private HttpURLConnection signed(String method, String path, TreeMap<String, String> params) throws Exception {
        String hostPort = hostOf(endpoint);
        String amzDate = utcDate("yyyyMMdd'T'HHmmss'Z'");
        String dateStamp = utcDate("yyyyMMdd");
        String payloadHash = sha256Hex("");

        // canonical query：参数按字典序 + RFC3986 全编码
        StringBuilder cq = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (cq.length() > 0) {
                cq.append('&');
            }
            cq.append(percentEncode(e.getKey())).append('=').append(percentEncode(e.getValue()));
        }
        String canonicalQuery = cq.toString();

        String canonicalHeaders = "host:" + hostPort + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = String.join("\n", method, path, canonicalQuery, canonicalHeaders, signedHeaders, payloadHash);
        String scope = dateStamp + "/us-east-1/s3/aws4_request";
        String stringToSign = String.join("\n", "AWS4-HMAC-SHA256", amzDate, scope, sha256Hex(canonicalRequest));

        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, "us-east-1");
        byte[] kService = hmac(kRegion, "s3");
        byte[] kSigning = hmac(kService, "aws4_request");
        String signature = hex(hmac(kSigning, stringToSign));

        String query = canonicalQuery;
        String urlStr = endpoint + path + (query.isEmpty() ? "" : "?" + query);
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("x-amz-date", amzDate);
        conn.setRequestProperty("x-amz-content-sha256", payloadHash);
        conn.setRequestProperty("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return conn;
    }

    // ==================== 小工具 ====================

    private String hostOf(String endpoint) {
        String s = endpoint.replaceFirst("^https?://", "");
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String utcDate(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern);
        f.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        return f.format(new Date());
    }

    private String percentEncode(String s) {
        try {
            String e = URLEncoder.encode(s, "UTF-8");
            return e.replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (Exception ex) {
            return s;
        }
    }

    private byte[] hmac(byte[] key, String data) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(String data) throws Exception {
        return hex(java.security.MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private List<String> regexAll(String text, String regex) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL).matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private String xmlUnescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
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
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}

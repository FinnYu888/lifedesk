package com.lifedesk.daily.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 每日速览数据服务。
 *
 * <p>提供三类外部数据拉取：</p>
 * <ul>
 *   <li>GitHub 热门项目：search API 按 created:>日期 + stars 排序取 Top 10，
 *       近似"过去 N 天获得星标最多"的热门趋势（GitHub 官方无 trending API）</li>
 *   <li>NBA 热点资讯：抓取虎扑 NBA 新闻页（SSR），解析文章链接与标题取前 15 条</li>
 *   <li>今日头条热榜：头条官方 hot-board 接口取前 15 条</li>
 * </ul>
 *
 * <p>HTTPS 容错：对以上外部请求统一使用信任所有证书的 SSLSocketFactory（仅作用于本次连接），
 * 附带桌面浏览器 User-Agent 避免被简单反爬拦截。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
public class DailyBriefingService {

    private static final String GITHUB_SEARCH_URL =
            "https://api.github.com/search/repositories?q=created%%3A%s&sort=stars&order=desc&per_page=10";

    private static final String HUPU_NBA_NEWS_URL = "https://www.hupu.com/nba/news";

    private static final String TOUTIAO_HOTBOARD_URL =
            "https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private static final int NBA_LIMIT = 15;
    private static final int TOUTIAO_LIMIT = 15;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 虎扑 NBA 新闻页的文章链接 + 标题（SSR 结构：<a href="https://bbs.hupu.com/{id}.html" class="list-item-title">...<div class="item-title-conent">标题</div>） */
    private static final Pattern HUPU_ITEM_PATTERN = Pattern.compile(
            "href=\"(https://bbs\\.hupu\\.com/\\d+\\.html)\"[^>]*class=\"list-item-title\"[^>]*>"
                    + "\\s*<div class=\"item-title-conent\">(.*?)</div>",
            Pattern.DOTALL);

    /** 信任所有证书的 SSLSocketFactory（仅作用于本服务发起的外部请求） */
    private static final SSLSocketFactory SSL_TRUST_ALL_FACTORY = createTrustAllSocketFactory();

    private static SSLSocketFactory createTrustAllSocketFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);
            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("初始化 trust-all SSLContext 失败", e);
        }
    }

    // ==================== GitHub 热门 Top 10 ====================

    /**
     * 拉取 GitHub 热门项目 Top 10。
     *
     * @param period day=过去24小时 / week=过去一周 / month=过去30天
     * @return 列表元素：fullName/description/stars/url/language/avatar；失败返回空列表
     */
    public List<Map<String, Object>> githubTrending(String period) {
        int days = "day".equals(period) ? 1 : "week".equals(period) ? 7 : 30;
        String date = LocalDate.now().minusDays(days).toString();
        String url = String.format(GITHUB_SEARCH_URL, date);
        log.info("GitHub 热门拉取：period={}, url={}", period, url);

        String body = httpGet(url, false);
        if (body == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode items = objectMapper.readTree(body).path("items");
            for (JsonNode item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fullName", item.path("full_name").asText(""));
                row.put("description", item.path("description").asText(""));
                row.put("stars", item.path("stargazers_count").asLong(0));
                row.put("url", item.path("html_url").asText(""));
                row.put("language", item.path("language").isNull() ? "" : item.path("language").asText(""));
                row.put("avatar", item.path("owner").path("avatar_url").asText(""));
                result.add(row);
            }
            log.info("GitHub 热门拉取成功：{} 条", result.size());
        } catch (Exception e) {
            log.warn("GitHub 热门解析失败：{}", e.getMessage());
        }
        return result;
    }

    // ==================== NBA 热点资讯 ====================

    /**
     * 拉取 NBA 热点资讯（虎扑 NBA 新闻页，取前 15 条）。
     *
     * @return 列表元素：title/url；失败返回空列表
     */
    public List<Map<String, Object>> nbaNews() {
        log.info("NBA 资讯拉取：{}", HUPU_NBA_NEWS_URL);
        String html = httpGet(HUPU_NBA_NEWS_URL, false);
        if (html == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Matcher matcher = HUPU_ITEM_PATTERN.matcher(html);
        List<String> seen = new ArrayList<>();
        while (matcher.find() && result.size() < NBA_LIMIT) {
            String url = matcher.group(1);
            if (seen.contains(url)) {
                continue;
            }
            seen.add(url);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", stripTags(matcher.group(2)));
            row.put("url", url);
            result.add(row);
        }
        log.info("NBA 资讯拉取成功：{} 条", result.size());
        return result;
    }

    // ==================== 今日头条热榜 ====================

    /**
     * 拉取今日头条热榜（官方 hot-board 接口，取前 15 条）。
     *
     * @return 列表元素：title/url/hotValue；失败返回空列表
     */
    public List<Map<String, Object>> toutiaoHot() {
        log.info("头条热榜拉取：{}", TOUTIAO_HOTBOARD_URL);
        String body = httpGet(TOUTIAO_HOTBOARD_URL, true);
        if (body == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            for (JsonNode item : data) {
                if (result.size() >= TOUTIAO_LIMIT) {
                    break;
                }
                String title = item.path("Title").asText("");
                if (title.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", title);
                row.put("url", item.path("Url").asText(""));
                row.put("hotValue", item.path("HotValue").asText(""));
                result.add(row);
            }
            log.info("头条热榜拉取成功：{} 条", result.size());
        } catch (Exception e) {
            log.warn("头条热榜解析失败：{}", e.getMessage());
        }
        return result;
    }

    // ==================== 通用 HTTP ====================

    /**
     * GET 拉取外部 URL 内容。
     *
     * @param url         目标地址
     * @param strictJson  true=要求 application/json 响应（GitHub API 需要 Accept 头）
     * @return 响应正文（UTF-8）；失败返回 null
     */
    private String httpGet(String url, boolean strictJson) {
        HttpURLConnection conn = null;
        try {
            URL target = new URL(url);
            conn = (HttpURLConnection) target.openConnection();
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(SSL_TRUST_ALL_FACTORY);
                https.setHostnameVerifier((hostname, session) -> true);
            }
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            if (strictJson) {
                conn.setRequestProperty("Accept", "application/vnd.github+json");
            } else {
                conn.setRequestProperty("Accept", "text/html,application/json,*/*");
            }
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status != 200) {
                log.warn("外部接口异常状态：url={}, status={}", url, status);
                return null;
            }
            try (InputStream is = conn.getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                }
                return sb.toString();
            }
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            log.warn("外部接口不可达：url={}, {}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("外部接口调用异常：url={}, {} {}", url, e.getClass().getSimpleName(), e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 去掉 HTML 标签并反转义常见实体 */
    private static String stripTags(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", "").trim();
        return text.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }
}

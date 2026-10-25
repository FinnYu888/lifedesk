package com.lifedesk.project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifedesk.project.entity.CommitActivity;
import com.lifedesk.project.entity.PullRequest;
import com.lifedesk.project.entity.ReleaseRecord;
import com.lifedesk.project.mapper.CommitActivityMapper;
import com.lifedesk.project.mapper.ProjectMapper;
import com.lifedesk.project.mapper.PullRequestMapper;
import com.lifedesk.project.mapper.ReleaseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 同步服务：根据仓库类型走不同同步通道。
 *
 * <ul>
 *   <li><b>github</b>：调用 GitHub REST API（api.github.com），鉴权用 Bearer PAT。
 *       能拿到准确的 PR 和 Release 元数据（不依赖 merge commit 解析）。</li>
 *   <li><b>gitlab</b>：调用 GitLab REST API（{scheme}://{host}/api/v4），支持 gitlab.com 与自建实例，
 *       鉴权用 PRIVATE-TOKEN header。能拿到准确的 MR 和 Release。</li>
 *   <li><b>generic</b>（默认）：JGit 浅克隆兜底，对其他 git 主机可用，PR 仅从 merge commit 推断。</li>
 * </ul>
 *
 * <p>同步策略：覆盖式（先按 project_id 清空旧的 commit/PR/release，再插入新的）。
 * 调用方异步触发（gitSyncExecutor），HTTP 请求线程不阻塞。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitSyncService {

    /** 同步时拉取的最大提交数 */
    private static final int MAX_COMMITS = 50;
    /** HTTP 请求超时（毫秒） */
    private static final int HTTP_TIMEOUT_MS = 15000;

    /** Jackson 单例 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Conventional Commits 前缀识别 */
    private static final Pattern TYPE_PATTERN = Pattern.compile("^\\s*(feat|fix|chore|refactor|docs|test|style|perf|build|ci)\\b", Pattern.CASE_INSENSITIVE);

    /** Merge pull request 解析：如 "Merge pull request #12 from org/branch" */
    private static final Pattern MERGE_PR_PATTERN = Pattern.compile("Merge pull request #(\\d+)", Pattern.CASE_INSENSITIVE);

    /** GitHub URL：https://github.com/{owner}/{repo}(.git) */
    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+?)(\\.git)?/?$", Pattern.CASE_INSENSITIVE);

    private final CommitActivityMapper commitMapper;
    private final PullRequestMapper prMapper;
    private final ReleaseRecordMapper releaseMapper;
    private final ProjectMapper projectMapper;

    /**
     * 同步入口：根据 project.gitPlatform 分发到对应实现。
     */
    public SyncResult sync(com.lifedesk.project.entity.Project project) {
        if (project.getGitUrl() == null || project.getGitUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("项目的 Git 地址为空，无法同步");
        }
        String platform = project.getGitPlatform() == null ? "generic" : project.getGitPlatform().trim().toLowerCase();
        // 标记同步中
        project.setSyncStatus("syncing");
        project.setSyncError(null);
        projectMapper.updateById(project);
        try {
            SyncResult result;
            switch (platform) {
                case "github":
                    result = syncViaGitHub(project);
                    break;
                case "gitlab":
                    result = syncViaGitLab(project);
                    break;
                default:
                    result = syncViaJGit(project);
                    break;
            }
            // 同步成功
            project.setSyncStatus("success");
            project.setSyncError(null);
            projectMapper.updateById(project);
            return result;
        } catch (Exception e) {
            log.error("[GitSync] 同步失败：项目={} 类型={} 错误={}", project.getName(), platform, e.getMessage(), e);
            String tip = friendlyError(e);
            // 失败也记录同步时间和错误信息，避免反复触发
            project.setLastSyncAt(LocalDateTime.now());
            project.setSyncStatus("failed");
            project.setSyncError(tip);
            projectMapper.updateById(project);
            return new SyncResult(0, 0, 0, tip);
        }
    }

    // ===================== GitHub REST API =====================

    private SyncResult syncViaGitHub(com.lifedesk.project.entity.Project project) throws Exception {
        String[] ownerRepo = parseOwnerRepo(project.getGitUrl());
        if (ownerRepo == null) {
            throw new IllegalArgumentException("GitHub 地址解析失败，应为 https://github.com/{owner}/{repo}.git");
        }
        String owner = ownerRepo[0];
        String repo = ownerRepo[1];
        String token = project.getGitPassword();
        String branch = branchOrDefault(project);
        log.info("[GitSync][GitHub] owner={} repo={} branch={}", owner, repo, branch);

        Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }

        String base = "https://api.github.com/repos/" + owner + "/" + repo;

        // 1. commits
        List<CommitActivity> commits = fetchGitHubCommits(base + "/commits?per_page=" + MAX_COMMITS + "&sha=" + urlEncode(branch), headers, project.getId());
        // 2. pulls
        List<PullRequest> prs = fetchGitHubPRs(base + "/pulls?state=all&per_page=30&sort=updated&direction=desc", headers, project.getId());
        // 3. releases
        List<ReleaseRecord> releases = fetchGitHubReleases(base + "/releases?per_page=20", headers, project.getId());

        persistAll(project, commits, prs, releases);
        return new SyncResult(commits.size(), prs.size(), releases.size(), null);
    }

    private List<CommitActivity> fetchGitHubCommits(String url, Map<String, String> headers, Long projectId) throws Exception {
        JsonNode root = httpGetJson(url, headers);
        List<CommitActivity> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode n : root) {
            CommitActivity ca = new CommitActivity();
            ca.setProjectId(projectId);
            ca.setSha(textOr(n, "sha"));
            JsonNode commit = n.get("commit");
            if (commit != null) {
                JsonNode author = commit.get("author");
                ca.setAuthor(author != null ? textOr(author, "name") : "unknown");
                String msg = textOr(commit, "message");
                String firstLine = msg.split("\\r?\\n", 2)[0];
                ca.setMessage(truncate(firstLine, 500));
                ca.setType(detectType(firstLine, false));
                String date = textOr(author, "date");
                ca.setCommittedAt(parseIso(date));
                ca.setTimeAgo(timeAgo(ca.getCommittedAt(), now));
            }
            result.add(ca);
        }
        return result;
    }

    private List<PullRequest> fetchGitHubPRs(String url, Map<String, String> headers, Long projectId) throws Exception {
        JsonNode root = httpGetJson(url, headers);
        List<PullRequest> result = new ArrayList<>();
        for (JsonNode n : root) {
            PullRequest pr = new PullRequest();
            pr.setProjectId(projectId);
            pr.setNumber("#" + n.get("number").asInt());
            pr.setTitle(textOr(n, "title"));
            JsonNode user = n.get("user");
            pr.setAuthor(user != null ? textOr(user, "login") : "unknown");
            String state = textOr(n, "state");
            String mergedAt = textOr(n, "merged_at");
            if (mergedAt != null && !mergedAt.isEmpty()) {
                pr.setState("merged");
                pr.setMergedAt(parseIso(mergedAt));
            } else {
                pr.setState(state);
            }
            JsonNode head = n.get("head");
            if (head != null) pr.setSourceBranch(textOr(head, "ref"));
            JsonNode base = n.get("base");
            if (base != null) pr.setTargetBranch(textOr(base, "ref"));
            pr.setMessage(textOr(n, "body"));
            result.add(pr);
        }
        return result;
    }

    private List<ReleaseRecord> fetchGitHubReleases(String url, Map<String, String> headers, Long projectId) throws Exception {
        JsonNode root = httpGetJson(url, headers);
        List<ReleaseRecord> result = new ArrayList<>();
        for (JsonNode n : root) {
            ReleaseRecord rr = new ReleaseRecord();
            rr.setProjectId(projectId);
            rr.setTag(textOr(n, "tag_name"));
            rr.setName(textOr(n, "name"));
            JsonNode author = n.get("author");
            rr.setAuthor(author != null ? textOr(author, "login") : "unknown");
            String published = textOr(n, "published_at");
            rr.setPublishedAt(parseIso(published));
            rr.setMessage(truncate(textOr(n, "body"), 2000));
            result.add(rr);
        }
        return result;
    }

    // ===================== GitLab REST API =====================

    private SyncResult syncViaGitLab(com.lifedesk.project.entity.Project project) throws Exception {
        String[] hostPath = parseGitLabUrl(project.getGitUrl());
        if (hostPath == null) {
            throw new IllegalArgumentException("GitLab 地址解析失败，应为 http(s)://{host}/{group}/{project}.git");
        }
        String schemeHost = hostPath[0];
        String projectPath = hostPath[1]; // 形如 group/sub/project（不含 .git）
        String projectId = urlEncode(projectPath);
        String token = project.getGitPassword();
        String branch = branchOrDefault(project);
        log.info("[GitSync][GitLab] host={} projectId={} branch={}", schemeHost, projectPath, branch);

        Map<String, String> headers = new java.util.HashMap<>();
        if (token != null && !token.isEmpty()) {
            headers.put("PRIVATE-TOKEN", token);
        }
        headers.put("Accept", "application/json");

        String base = schemeHost + "/api/v4/projects/" + projectId;

        // 1. commits
        List<CommitActivity> commits = fetchGitLabCommits(base + "/repository/commits?ref_name=" + urlEncode(branch) + "&per_page=" + MAX_COMMITS, headers, project.getId());
        // 2. merge requests
        List<PullRequest> prs = fetchGitLabMRs(base + "/merge_requests?per_page=30&order_by=updated_at", headers, project.getId());
        // 3. releases
        List<ReleaseRecord> releases = fetchGitLabReleases(base + "/releases?per_page=20", headers, project.getId());

        persistAll(project, commits, prs, releases);
        return new SyncResult(commits.size(), prs.size(), releases.size(), null);
    }

    private List<CommitActivity> fetchGitLabCommits(String url, Map<String, String> headers, Long projectId) throws Exception {
        JsonNode root = httpGetJson(url, headers);
        List<CommitActivity> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode n : root) {
            CommitActivity ca = new CommitActivity();
            ca.setProjectId(projectId);
            ca.setSha(textOr(n, "id"));
            ca.setAuthor(textOr(n, "author_name"));
            String msg = textOr(n, "title");
            if (msg == null || msg.isEmpty()) msg = textOr(n, "message");
            String firstLine = msg == null ? "" : msg.split("\\r?\\n", 2)[0];
            ca.setMessage(truncate(firstLine, 255));
            ca.setType(detectType(firstLine, false));
            ca.setCommittedAt(parseIso(textOr(n, "committed_date")));
            ca.setTimeAgo(timeAgo(ca.getCommittedAt(), now));
            result.add(ca);
        }
        return result;
    }

    private List<PullRequest> fetchGitLabMRs(String url, Map<String, String> headers, Long projectId) throws Exception {
        JsonNode root = httpGetJson(url, headers);
        List<PullRequest> result = new ArrayList<>();
        for (JsonNode n : root) {
            PullRequest pr = new PullRequest();
            pr.setProjectId(projectId);
            pr.setNumber("!" + n.get("iid").asInt());
            pr.setTitle(textOr(n, "title"));
            JsonNode author = n.get("author");
            pr.setAuthor(author != null ? textOr(author, "username") : "unknown");
            String state = textOr(n, "state");
            if ("merged".equals(state)) {
                pr.setMergedAt(parseIso(textOr(n, "merged_at")));
            }
            pr.setState(state);
            pr.setSourceBranch(textOr(n, "source_branch"));
            pr.setTargetBranch(textOr(n, "target_branch"));
            pr.setMessage(truncate(textOr(n, "description"), 2000));
            result.add(pr);
        }
        return result;
    }

    private List<ReleaseRecord> fetchGitLabReleases(String url, Map<String, String> headers, Long projectId) throws Exception {
        JsonNode root = httpGetJson(url, headers);
        List<ReleaseRecord> result = new ArrayList<>();
        for (JsonNode n : root) {
            ReleaseRecord rr = new ReleaseRecord();
            rr.setProjectId(projectId);
            rr.setTag(textOr(n, "tag_name"));
            rr.setName(textOr(n, "name"));
            JsonNode author = n.get("author");
            rr.setAuthor(author != null ? textOr(author, "username") : "unknown");
            // GitLab releases: released_at 或 created_at
            String published = textOr(n, "released_at");
            if (published == null || published.isEmpty()) published = textOr(n, "created_at");
            rr.setPublishedAt(parseIso(published));
            rr.setMessage(truncate(textOr(n, "description"), 2000));
            result.add(rr);
        }
        return result;
    }

    // ===================== JGit 通用兜底 =====================

    private SyncResult syncViaJGit(com.lifedesk.project.entity.Project project) throws Exception {
        String branch = branchOrDefault(project);
        File tmpDir = null;
        Git git = null;
        try {
            tmpDir = Files.createTempDirectory("lifedesk-git-").toFile();
            log.info("[GitSync][JGit] 开始克隆 {} 分支 {} 到 {}", project.getGitUrl(), branch, tmpDir.getAbsolutePath());

            CloneCommand cloneCmd = Git.cloneRepository()
                    .setURI(project.getGitUrl().trim())
                    .setDirectory(tmpDir)
                    .setBranch(branch)
                    .setCloneSubmodules(false);
            String user = project.getGitUsername();
            String pwd = project.getGitPassword();
            if (user != null && !user.trim().isEmpty() && pwd != null && !pwd.isEmpty()) {
                cloneCmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, pwd));
            }
            git = cloneCmd.call();
            log.info("[GitSync][JGit] 克隆成功，开始解析提交与标签");

            List<CommitActivity> commits = parseCommitsViaJGit(git, project.getId());
            List<PullRequest> prs = extractPrsFromCommits(commits, project.getId());
            List<ReleaseRecord> releases = parseTagsViaJGit(git, project.getId());

            persistAll(project, commits, prs, releases);
            return new SyncResult(commits.size(), prs.size(), releases.size(), null);
        } finally {
            if (git != null) {
                try { git.close(); } catch (Exception ignored) {}
            }
            if (tmpDir != null) {
                deleteQuietly(tmpDir);
            }
        }
    }

    private List<CommitActivity> parseCommitsViaJGit(Git git, Long projectId) throws GitAPIException {
        List<CommitActivity> result = new ArrayList<>();
        Iterable<RevCommit> log = git.log().setMaxCount(MAX_COMMITS).call();
        LocalDateTime now = LocalDateTime.now();
        for (RevCommit rc : log) {
            CommitActivity ca = new CommitActivity();
            ca.setProjectId(projectId);
            String authorName = rc.getAuthorIdent().getName();
            ca.setAuthor(authorName == null ? "unknown" : authorName);
            String fullMsg = rc.getFullMessage() == null ? "" : rc.getFullMessage().trim();
            String firstLine = fullMsg.split("\\r?\\n", 2)[0];
            ca.setMessage(truncate(firstLine, 255));
            ca.setType(detectType(firstLine, rc.getParentCount() > 1));
            Date commitDate = rc.getAuthorIdent().getWhen();
            LocalDateTime committedAt = commitDate == null ? null
                    : LocalDateTime.ofInstant(commitDate.toInstant(), ZoneId.systemDefault());
            ca.setCommittedAt(committedAt);
            ca.setTimeAgo(timeAgo(committedAt, now));
            ca.setSha(rc.getId().getName());
            result.add(ca);
        }
        return result;
    }

    private List<PullRequest> extractPrsFromCommits(List<CommitActivity> commits, Long projectId) {
        List<PullRequest> prs = new ArrayList<>();
        for (CommitActivity c : commits) {
            if (!"merge".equals(c.getType())) continue;
            Matcher m = MERGE_PR_PATTERN.matcher(c.getMessage());
            if (!m.find()) continue;
            PullRequest pr = new PullRequest();
            pr.setProjectId(projectId);
            pr.setNumber("#" + m.group(1));
            String[] parts = c.getMessage().split("\\s+");
            if (parts.length >= 5) pr.setSourceBranch(parts[parts.length - 1]);
            pr.setTargetBranch("main");
            pr.setTitle(truncate(c.getMessage(), 500));
            pr.setAuthor(c.getAuthor());
            pr.setState("merged");
            pr.setMergedAt(c.getCommittedAt());
            pr.setMessage(c.getMessage());
            prs.add(pr);
        }
        return prs;
    }

    private List<ReleaseRecord> parseTagsViaJGit(Git git, Long projectId) {
        List<ReleaseRecord> result = new ArrayList<>();
        try {
            List<Ref> tags = git.tagList().call();
            try (Repository repo = git.getRepository(); RevWalk walk = new RevWalk(repo)) {
                for (Ref tag : tags) {
                    try {
                        RevCommit rc = walk.parseCommit(tag.getObjectId());
                        ReleaseRecord rr = new ReleaseRecord();
                        rr.setProjectId(projectId);
                        String tagName = tag.getName().replace("refs/tags/", "");
                        rr.setTag(truncate(tagName, 128));
                        rr.setName(truncate(tagName, 255));
                        String authorName = rc.getAuthorIdent().getName();
                        rr.setAuthor(authorName == null ? "unknown" : authorName);
                        Date when = rc.getAuthorIdent().getWhen();
                        rr.setPublishedAt(when == null ? null
                                : LocalDateTime.ofInstant(when.toInstant(), ZoneId.systemDefault()));
                        rr.setMessage(truncate(rc.getShortMessage(), 500));
                        result.add(rr);
                    } catch (Exception inner) {
                        log.warn("[GitSync][JGit] 解析 tag {} 失败：{}", tag.getName(), inner.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[GitSync][JGit] 拉取 tag 失败：{}", e.getMessage());
        }
        return result;
    }

    // ===================== 共用辅助 =====================

    /** 持久化：清空旧数据 → 插入新数据 → 更新 project 统计字段 */
    private void persistAll(com.lifedesk.project.entity.Project project,
                            List<CommitActivity> commits,
                            List<PullRequest> prs,
                            List<ReleaseRecord> releases) {
        clearProjectGitData(project.getId());
        commits.forEach(commitMapper::insert);
        prs.forEach(prMapper::insert);
        releases.forEach(releaseMapper::insert);

        project.setLastSyncAt(LocalDateTime.now());
        project.setCommitsCount(commits.size());
        project.setPrsCount(prs.size());
        project.setReleasesCount(releases.size());
        project.setLastActive("刚刚");
        projectMapper.updateById(project);

        log.info("[GitSync] 同步完成：项目={} 提交={} PR={} 发布={}",
                project.getName(), commits.size(), prs.size(), releases.size());
    }

    /** 清空指定项目下的 git 同步数据（物理删除） */
    private void clearProjectGitData(Long projectId) {
        if (projectId == null) return;
        commitMapper.delete(new QueryWrapper<CommitActivity>().eq("project_id", projectId));
        prMapper.delete(new QueryWrapper<PullRequest>().eq("project_id", projectId));
        releaseMapper.delete(new QueryWrapper<ReleaseRecord>().eq("project_id", projectId));
    }

    private String branchOrDefault(com.lifedesk.project.entity.Project project) {
        return project.getGitBranch() == null || project.getGitBranch().trim().isEmpty()
                ? "main" : project.getGitBranch().trim();
    }

    /** 解析 GitHub URL → [owner, repo]，失败返回 null */
    private String[] parseOwnerRepo(String gitUrl) {
        Matcher m = GITHUB_URL_PATTERN.matcher(gitUrl.trim());
        if (m.matches()) {
            return new String[]{m.group(2), m.group(3)};
        }
        return null;
    }

    /** 解析 GitLab URL → [schemeHost, projectPath]，失败返回 null。
     *  schemeHost 形如 "http://10.139.92.114:8080"；projectPath 形如 "group/sub/project"（不含 .git） */
    private String[] parseGitLabUrl(String gitUrl) {
        if (gitUrl == null) return null;
        String url = gitUrl.trim();
        // 匹配 scheme://host[:port]/path/to/project.git
        Pattern p = Pattern.compile("^(https?://[^/]+)/(\\S+?)(?:\\.git)?/?$", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(url);
        if (m.matches()) {
            return new String[]{m.group(1), m.group(2)};
        }
        return null;
    }

    // ===================== HTTP / JSON 工具 =====================

    private JsonNode httpGetJson(String urlStr, Map<String, String> headers) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(HTTP_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_TIMEOUT_MS);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        int code = conn.getResponseCode();
        if (code >= 400) {
            String body = readStream(conn.getErrorStream());
            throw new IOException("HTTP " + code + ": " + truncate(body, 200));
        }
        String body = readStream(conn.getInputStream());
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new IOException("JSON 解析失败: " + truncate(body, 200), e);
        }
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            // ignore
        }
        return sb.toString();
    }

    private String textOr(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return s == null || s.isEmpty() ? null : s;
    }

    /** 解析 ISO 8601 时间字符串（GitHub/GitLab 都用 ISO），失败返回 null */
    private LocalDateTime parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            // 兼容 2026-10-24T22:00:00Z 与 2026-10-24T22:00:00+08:00
            String s = iso.replace("Z", "+00:00");
            return java.time.OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(iso.replace(" ", "T"));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private String detectType(String message, boolean isMerge) {
        if (isMerge && MERGE_PR_PATTERN.matcher(message).find()) return "merge";
        Matcher m = TYPE_PATTERN.matcher(message);
        if (m.find()) return m.group(1).toLowerCase();
        return "chore";
    }

    private String timeAgo(LocalDateTime committed, LocalDateTime now) {
        if (committed == null) return "";
        Duration d = Duration.between(committed, now);
        long minutes = d.toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + " 分钟前";
        long hours = d.toHours();
        if (hours < 24) return hours + " 小时前";
        long days = d.toDays();
        if (days < 7) return days + " 天前";
        return committed.format(DateTimeFormatter.ofPattern("MM-dd"));
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private void deleteQuietly(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteQuietly(c);
        }
        f.setWritable(true, false);
        if (!f.delete()) f.deleteOnExit();
    }

    private String friendlyError(Exception e) {
        String msg = Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
        String lower = msg.toLowerCase();
        if (lower.contains("not authorized") || lower.contains("auth") || lower.contains("401") || lower.contains("403")) {
            return "Git 鉴权失败：请检查用户名/密码或 Access Token 是否正确（GitHub 用 PAT 作密码，GitLab 用 PRIVATE-TOKEN）";
        }
        if (lower.contains("unknownhost") || lower.contains("connect") || lower.contains("timed out") || lower.contains("timeout")) {
            return "Git 网络连接失败：无法访问仓库地址（可能是网络限制或地址错误）";
        }
        if (lower.contains("ref not found") || lower.contains("refusing to fetch") || lower.contains("branch")) {
            return "Git 分支不存在或不可达：" + msg;
        }
        return "Git 同步失败：" + msg;
    }

    /** 同步结果摘要 */
    public static class SyncResult {
        public final int commits;
        public final int prs;
        public final int releases;
        public final String error;

        SyncResult(int commits, int prs, int releases, String error) {
            this.commits = commits;
            this.prs = prs;
            this.releases = releases;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null;
        }
    }
}

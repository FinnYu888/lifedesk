package com.lifedesk.project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.project.entity.CommitActivity;
import com.lifedesk.project.entity.CommitActivityVO;
import com.lifedesk.project.entity.Milestone;
import com.lifedesk.project.entity.Project;
import com.lifedesk.project.entity.PullRequest;
import com.lifedesk.project.entity.ReleaseRecord;
import com.lifedesk.project.mapper.CommitActivityMapper;
import com.lifedesk.project.mapper.MilestoneMapper;
import com.lifedesk.project.mapper.ProjectMapper;
import com.lifedesk.project.mapper.PullRequestMapper;
import com.lifedesk.project.mapper.ReleaseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 项目管理 Service。
 *
 * <p>统计卡数据基于真实 commit_activity/pull_request/release_record 表聚合，
 * 已删除项目（deleted=1）不参与统计与列表展示。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final MilestoneMapper milestoneMapper;
    private final CommitActivityMapper commitActivityMapper;
    private final PullRequestMapper pullRequestMapper;
    private final ReleaseRecordMapper releaseRecordMapper;
    private final GitSyncService gitSyncService;

    /**
     * Git 同步专用线程池（按 bean 名注入，避免与 Spring 自带 Executor 冲突）。
     * 用于异步执行 JGit clone，HTTP 请求线程立即返回，避免前端 axios 超时。
     */
    @javax.annotation.Resource(name = "gitSyncExecutor")
    private Executor gitSyncExecutor;

    /**
     * 统计卡数据。
     * <p><b>本周提交/合并请求/本周新增仓库</b> 都直接基于活跃项目自身的字段聚合，
     * 反映已同步项目的实时状态（不再依赖 commit_activity 行级聚合）。</p>
     * <ul>
     *   <li>active_projects：未删除项目总数</li>
     *   <li>week_commits：所有活跃项目 commits_count 字段总和</li>
     *   <li>pending_prs：所有活跃项目 prs_count 字段总和</li>
     *   <li>new_repos：本周新建项目数（create_time 近 7 天）</li>
     * </ul>
     */
    public Map<String, Object> getStat() {
        List<Project> projects = projectMapper.selectList(null);
        long activeCount = projects.size();

        // 本周提交：所有活跃项目 commits_count 字段累加（反映已同步数据）
        long weekCommits = 0;
        long prsTotal = 0;
        for (Project p : projects) {
            if (p.getCommitsCount() != null) weekCommits += p.getCommitsCount();
            if (p.getPrsCount() != null) prsTotal += p.getPrsCount();
        }

        // 本周新建项目数
        LocalDate weekStart = LocalDate.now().minusDays(6);
        long newReposWeek = projects.stream()
                .filter(p -> p.getCreateTime() != null
                        && !p.getCreateTime().toLocalDate().isBefore(weekStart))
                .count();

        Map<String, Object> stat = new HashMap<>();
        stat.put("active_projects", activeCount);
        stat.put("week_commits", weekCommits);
        stat.put("week_commits_trend", weekCommits > 0 ? "+" + weekCommits : "+0");
        stat.put("pending_prs", prsTotal);
        stat.put("pending_prs_to_review", 0);
        stat.put("new_repos", newReposWeek);
        stat.put("new_repos_this_week", newReposWeek);
        return stat;
    }

    /** 活跃项目列表（已删除项目自动过滤，因为 ProjectMapper 配置了 @TableLogic） */
    public List<Project> listActive() {
        return projectMapper.selectList(new QueryWrapper<Project>().orderByDesc("update_time"));
    }

    public List<Milestone> listMilestones() {
        return milestoneMapper.selectList(null);
    }

    /**
     * 最近提交活动：每个项目的最近一条 commit（关联项目名）。
     * <p>已删除项目/已删除 commit 自动过滤（SQL 中 deleted=0 双重过滤）。
     * 返回顺序：committed_at 倒序。前端可看到每个活跃项目的最后一次提交。</p>
     */
    public List<CommitActivityVO> listCommitActivities() {
        return commitActivityMapper.selectLatestPerProject();
    }

    /** 按项目列出 PR */
    public List<PullRequest> listPrs(Long projectId) {
        return pullRequestMapper.selectList(new QueryWrapper<PullRequest>()
                .eq("project_id", projectId)
                .orderByDesc("merged_at"));
    }

    /** 按项目列出发布 */
    public List<ReleaseRecord> listReleases(Long projectId) {
        return releaseRecordMapper.selectList(new QueryWrapper<ReleaseRecord>()
                .eq("project_id", projectId)
                .orderByDesc("published_at"));
    }

    /**
     * 新建项目。
     * <p>项目立即写入数据库并返回。若填了 git_url，则**异步**触发首次同步
     * （不阻塞 HTTP 请求线程，避免前端 axios 超时）。用户可稍后刷新列表查看同步结果。</p>
     */
    public Project create(Project project) {
        if (project.getName() == null || project.getName().trim().isEmpty()) {
            throw new BizException("项目名不能为空");
        }
        if (project.getProgress() == null) {
            project.setProgress(0);
        }
        if (project.getStatus() == null) {
            project.setStatus("normal");
        }
        if (project.getGitBranch() == null || project.getGitBranch().trim().isEmpty()) {
            project.setGitBranch("main");
        }
        if (project.getGitPlatform() == null || project.getGitPlatform().trim().isEmpty()) {
            project.setGitPlatform("generic");
        }
        if (project.getCommitsCount() == null) project.setCommitsCount(0);
        if (project.getPrsCount() == null) project.setPrsCount(0);
        if (project.getReleasesCount() == null) project.setReleasesCount(0);
        if (project.getSyncStatus() == null) project.setSyncStatus("pending");
        if (project.getLastActive() == null) project.setLastActive("刚刚");
        projectMapper.insert(project);

        // 若填了 git 信息，异步触发首次同步
        if (project.getGitUrl() != null && !project.getGitUrl().trim().isEmpty()) {
            final Long pid = project.getId();
            gitSyncExecutor.execute(() -> {
                try {
                    Project fresh = projectMapper.selectById(pid);
                    if (fresh != null) {
                        gitSyncService.sync(fresh);
                    }
                } catch (Exception e) {
                    log.warn("项目 {} 创建后异步同步失败：{}", project.getName(), e.getMessage());
                }
            });
        }
        return project;
    }

    /**
     * 删除项目（软删）。
     * <p>同时级联清理该项目下的 commit_activity/pull_request/release_record，
     * 确保已删除项目不再参与任何统计。</p>
     */
    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new BizException("项目 ID 不能为空");
        }
        Project existed = projectMapper.selectById(id);
        if (existed == null) {
            throw new BizException("项目不存在或已被删除");
        }
        // 物理删除关联的 git 数据，避免统计接口（基于未删除项目）误统计
        commitActivityMapper.delete(new QueryWrapper<CommitActivity>().eq("project_id", id));
        pullRequestMapper.delete(new QueryWrapper<PullRequest>().eq("project_id", id));
        releaseRecordMapper.delete(new QueryWrapper<ReleaseRecord>().eq("project_id", id));
        // 项目软删
        projectMapper.deleteById(id);
        log.info("项目 {} 已删除（软删），关联的 commit/PR/release 已清理", existed.getName());
    }

    /**
     * 编辑项目：UPDATE 基本信息 + Git 配置。
     * <p>若 git_url 改动或非空，触发异步重新同步（不阻塞 HTTP 请求线程）。</p>
     */
    public Project update(Long id, Project incoming) {
        if (id == null) {
            throw new BizException("项目 ID 不能为空");
        }
        Project existed = projectMapper.selectById(id);
        if (existed == null) {
            throw new BizException("项目不存在或已被删除");
        }
        // 检查 git 配置是否变化（决定是否触发重新同步）
        boolean gitChanged = isFieldChanged(existed.getGitUrl(), incoming.getGitUrl())
                || isFieldChanged(existed.getGitUsername(), incoming.getGitUsername())
                || isFieldChanged(existed.getGitPassword(), incoming.getGitPassword())
                || isFieldChanged(existed.getGitBranch(), incoming.getGitBranch())
                || isFieldChanged(existed.getGitPlatform(), incoming.getGitPlatform());

        // 应用更新（基本信息 + git 配置）
        if (incoming.getName() != null && !incoming.getName().trim().isEmpty()) {
            existed.setName(incoming.getName().trim());
        }
        if (incoming.getTech() != null) existed.setTech(incoming.getTech().trim());
        if (incoming.getDescription() != null) existed.setDescription(incoming.getDescription());
        if (incoming.getStatus() != null) existed.setStatus(incoming.getStatus());
        if (incoming.getProgress() != null) existed.setProgress(incoming.getProgress());
        if (incoming.getGitUrl() != null) existed.setGitUrl(incoming.getGitUrl().trim());
        if (incoming.getGitUsername() != null) existed.setGitUsername(incoming.getGitUsername());
        if (incoming.getGitPassword() != null) existed.setGitPassword(incoming.getGitPassword());
        if (incoming.getGitBranch() != null && !incoming.getGitBranch().trim().isEmpty()) {
            existed.setGitBranch(incoming.getGitBranch().trim());
        }
        if (incoming.getGitPlatform() != null && !incoming.getGitPlatform().trim().isEmpty()) {
            existed.setGitPlatform(incoming.getGitPlatform().trim());
        }
        projectMapper.updateById(existed);

        // 若 git 配置变化且新的 git_url 非空，异步触发重新同步
        if (gitChanged && existed.getGitUrl() != null && !existed.getGitUrl().trim().isEmpty()) {
            final Long pid = id;
            gitSyncExecutor.execute(() -> {
                try {
                    Project fresh = projectMapper.selectById(pid);
                    if (fresh != null) {
                        gitSyncService.sync(fresh);
                    }
                } catch (Exception e) {
                    log.warn("项目 {} 编辑后异步同步失败：{}", existed.getName(), e.getMessage());
                }
            });
        }
        return existed;
    }

    private boolean isFieldChanged(String old, String inc) {
        if (inc == null) return false;
        String o = old == null ? "" : old;
        return !o.equals(inc);
    }

    /**
     * 同步指定项目的 git 数据。**异步触发**，立即返回，避免 HTTP 请求阻塞。
     * <p>用户点击「同步」按钮后稍等几秒刷新列表即可看到最新提交/PR/发布数。</p>
     */
    public GitSyncService.SyncResult syncProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException("项目不存在或已被删除");
        }
        if (project.getGitUrl() == null || project.getGitUrl().trim().isEmpty()) {
            throw new BizException("该项目未配置 Git 地址，无法同步");
        }
        // 异步触发同步
        final Long pid = id;
        gitSyncExecutor.execute(() -> {
            try {
                Project fresh = projectMapper.selectById(pid);
                if (fresh != null) {
                    gitSyncService.sync(fresh);
                }
            } catch (Exception e) {
                log.warn("项目 {} 手动同步失败：{}", project.getName(), e.getMessage());
            }
        });
        // 立即返回"已开始"提示，前端按提示稍后刷新查看
        return new GitSyncService.SyncResult(0, 0, 0, null) {
            @Override
            public boolean isSuccess() {
                return true;
            }
        };
    }
}

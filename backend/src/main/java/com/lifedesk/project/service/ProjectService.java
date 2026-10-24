package com.lifedesk.project.service;

import com.lifedesk.common.BizException;
import com.lifedesk.project.entity.CommitActivity;
import com.lifedesk.project.entity.Milestone;
import com.lifedesk.project.entity.Project;
import com.lifedesk.project.mapper.CommitActivityMapper;
import com.lifedesk.project.mapper.MilestoneMapper;
import com.lifedesk.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目管理 Service。
 *
 * <p>对应 03_项目管理.md 中的统计卡、活跃项目列表、里程碑进度、提交动态。</p>
 *
 * @author LifeDesk
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final MilestoneMapper milestoneMapper;
    private final CommitActivityMapper commitActivityMapper;

    /** 统计卡数据（设计文档：活跃项目 12 / 本周提交 24 / 待合并 PR 5 / 新仓库 8） */
    public Map<String, Object> getStat() {
        Long activeCount = projectMapper.selectCount(null);
        Map<String, Object> stat = new HashMap<>();
        stat.put("active_projects", activeCount);
        stat.put("today_commits", 24);
        stat.put("week_commits", 24);
        stat.put("week_commits_trend", "+12%");
        stat.put("pending_prs", 5);
        stat.put("pending_prs_to_review", 2);
        stat.put("new_repos", 8);
        stat.put("new_repos_this_week", 1);
        return stat;
    }

    public List<Project> listActive() {
        return projectMapper.selectList(null);
    }

    public List<Milestone> listMilestones() {
        return milestoneMapper.selectList(null);
    }

    public List<CommitActivity> listCommitActivities() {
        return commitActivityMapper.selectList(null);
    }

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
        projectMapper.insert(project);
        return project;
    }
}
package com.lifedesk.project.controller;

import com.lifedesk.common.R;
import com.lifedesk.project.entity.CommitActivity;
import com.lifedesk.project.entity.Milestone;
import com.lifedesk.project.entity.Project;
import com.lifedesk.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目管理 Controller。
 *
 * <p>路由前缀 /api/project（context-path + mapping）。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /** 统计卡行 */
    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        return R.ok(projectService.getStat());
    }

    /** 活跃项目列表 */
    @GetMapping("/active")
    public R<List<Project>> active() {
        return R.ok(projectService.listActive());
    }

    /** 里程碑进度 */
    @GetMapping("/milestones")
    public R<List<Milestone>> milestones() {
        return R.ok(projectService.listMilestones());
    }

    /** 最近提交活动 */
    @GetMapping("/commits")
    public R<List<CommitActivity>> commits() {
        return R.ok(projectService.listCommitActivities());
    }

    /** 新建项目 */
    @PostMapping
    public R<Project> create(@RequestBody Project project) {
        return R.ok(projectService.create(project));
    }

    /** 项目看板聚合（首页用） */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("stat", projectService.getStat());
        data.put("active_projects", projectService.listActive());
        data.put("milestones", projectService.listMilestones());
        data.put("commits", projectService.listCommitActivities());
        return R.ok(data);
    }
}
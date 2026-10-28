package com.lifedesk.project.controller;

import com.lifedesk.common.R;
import com.lifedesk.project.entity.CommitActivityVO;
import com.lifedesk.project.entity.Milestone;
import com.lifedesk.project.entity.Project;
import com.lifedesk.project.entity.PullRequest;
import com.lifedesk.project.entity.ReleaseRecord;
import com.lifedesk.project.entity.Requirement;
import com.lifedesk.project.service.GitSyncService;
import com.lifedesk.project.service.ProjectService;
import com.lifedesk.project.service.RequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目管理 Controller。
 *
 * <p>路由前缀 /api/project（context-path + mapping）。
 * 支持新建项目（含 git 地址/用户名/密码/分支，自动同步）、删除项目（软删 + 级联清理）、
 * 手动同步、查询 PR/发布记录。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final RequirementService requirementService;
    private final GitSyncService gitSyncService;

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

    /** 里程碑进度：从需求接口查询到的 sprint 聚合（CDS20261025 形式，后 8 位为里程碑日期） */
    @GetMapping("/milestones")
    public R<List<Milestone>> milestones() {
        return R.ok(requirementService.listMilestones());
    }

    /** 最近提交活动：每个项目的最近一条 commit（含项目名） */
    @GetMapping("/commits")
    public R<List<CommitActivityVO>> commits() {
        return R.ok(projectService.listCommitActivities());
    }

    /**
     * 我的需求：从第三方需求平台 {@code https://wetrans.cimc.cn/gateway/apiIssue/developTask/query} 拉取。
     * <p>远程接口暂不通，回退本地 mock 表，后续提供 token 后无缝切换。</p>
     *
     * @param sprint  可选 sprint 过滤（CDS20261025）
     * @param status  可选任务状态过滤：todo/doing/testing/pending/done
     * @param keyword 可选关键词，按 任务详情/关联话题/产品/测试人 模糊匹配
     */
    @GetMapping("/requirements")
    public R<List<Requirement>> requirements(@RequestParam(required = false) String sprint,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String keyword) {
        return R.ok(requirementService.list(sprint, status, keyword));
    }

    /**
     * 我的需求详情：用户点击列表行触发，调用第三方接口
     * {@code GET https://wetrans.cimc.cn/gateway/apiIssue/developTask/getDetail?id=<sourceId>}。
     *
     * <p>鉴权头与列表接口一致（{@code Authorization: <token>}）。返回原始详情对象，
     * 含 content HTML（含 img 标签），前端用 v-html 渲染图片。</p>
     *
     * @param sourceId 需求在第三方平台的 ID（即列表行的 sourceId 字段）
     */
    @GetMapping("/requirements/{sourceId}/detail")
    public R<Map<String, Object>> requirementDetail(@PathVariable String sourceId) {
        return R.ok(requirementService.fetchDetail(sourceId));
    }

    /**
     * 设置最新 authorization token（用户在"我的需求"卡片输入后调用）。
     *
     * <p>{@code wetrans.cimc.cn/gateway/} 开头的接口鉴权信息每日可能过期，
     * 用户输入最新 token 后，立即对所有 fetch* 方法生效，并触发前端刷新"我的需求"+"里程碑进度"。</p>
     *
     * <p>请求体：{@code {"token": "<JWT，不带 Bearer 前缀>"}}，传空则清空动态 token</p>
     */
    @PostMapping("/requirements/authorization")
    public R<Map<String, Object>> setAuthorization(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("token");
        requirementService.setAuthorization(token);
        return R.ok(requirementService.getAuthorizationStatus());
    }

    /**
     * 查询当前 authorization 设置状态（不返回 token 本身，避免泄漏）。
     *
     * <p>返回：{@code {"hasDynamicToken":bool, "hasFallbackToken":bool, "hasEffectiveToken":bool, "source":"dynamic|fallback|none"}}
     * 前端用于展示"是否已设置 authorization"以及"是动态设置还是配置文件兜底"。</p>
     */
    @GetMapping("/requirements/authorization/status")
    public R<Map<String, Object>> authorizationStatus() {
        return R.ok(requirementService.getAuthorizationStatus());
    }

    /**
     * 图片代理：用 Authorization 头去远程拉取图片字节流，原样透传给前端。
     *
     * <p>用途：详情弹窗 content HTML 中的 {@code <img src="...">} 标签，浏览器渲染时无法附加
     * 自定义 Authorization 头，对需要鉴权的图片资源会收到 401。前端把 img src 改写为
     * {@code /api/project/requirements/img-proxy?url=<encoded>} 走本端点代理。</p>
     *
     * <p>失败返回 404（浏览器 img 标签会展示占位图）。</p>
     *
     * @param url 远程图片绝对 URL（必须 http/https 协议）
     */
    @GetMapping("/requirements/img-proxy")
    public ResponseEntity<byte[]> imgProxy(@RequestParam String url) {
        byte[] data = requirementService.fetchImage(url);
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(requirementService.guessImageContentType(url)));
        // 浏览器可缓存 1 小时，减少重复代理请求
        headers.setCacheControl("public, max-age=3600");
        return new ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
    }

    /** 新建项目 */
    @PostMapping
    public R<Project> create(@RequestBody Project project) {
        return R.ok(projectService.create(project));
    }

    /** 编辑项目：UPDATE 基本信息 + Git 配置；git 配置变化会触发异步重新同步 */
    @PutMapping("/{id}")
    public R<Project> update(@PathVariable Long id, @RequestBody Project project) {
        return R.ok(projectService.update(id, project));
    }

    /** 删除项目（软删 + 级联清理 commit/PR/release，已删除项目不再参与统计） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return R.ok();
    }

    /** 同步指定项目的 git 数据（提交/PR/发布） */
    @PostMapping("/{id}/sync")
    public R<GitSyncService.SyncResult> sync(@PathVariable Long id) {
        return R.ok(projectService.syncProject(id));
    }

    /**
     * 拉取指定项目已配置仓库的分支列表（编辑页「使用分支」下拉数据源）。
     * <p>使用项目已保存的地址/账密；编辑弹窗密码留空表示不修改，这里直接读库里的真实凭据。</p>
     * <p>失败抛 BizException/IllegalArgumentException（message 为友好提示，由全局异常处理返回）。</p>
     */
    @GetMapping("/{id}/branches")
    public R<List<String>> branches(@PathVariable Long id) {
        return R.ok(projectService.listProjectBranches(id));
    }

    /**
     * 用表单临时输入的地址/账密拉取分支列表（新建项目时「使用分支」下拉数据源）。
     * <p>请求体：{@code {"gitPlatform":"generic|github|gitlab","gitUrl":"...","gitUsername":"...","gitPassword":"..."}}</p>
     */
    @PostMapping("/branches")
    public R<List<String>> branchesPreview(@RequestBody Map<String, String> body) {
        if (body == null) {
            return R.ok(java.util.Collections.emptyList());
        }
        return R.ok(gitSyncService.listBranches(
                body.get("gitPlatform"), body.get("gitUrl"),
                body.get("gitUsername"), body.get("gitPassword")));
    }

    /** 列出项目下的合并请求 */
    @GetMapping("/{id}/prs")
    public R<List<PullRequest>> prs(@PathVariable Long id) {
        return R.ok(projectService.listPrs(id));
    }

    /** 列出项目下的发布记录 */
    @GetMapping("/{id}/releases")
    public R<List<ReleaseRecord>> releases(@PathVariable Long id) {
        return R.ok(projectService.listReleases(id));
    }

    /** 项目看板聚合（首页用） */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("stat", projectService.getStat());
        data.put("active_projects", projectService.listActive());
        data.put("milestones", requirementService.listMilestones());
        data.put("requirements", requirementService.list(null, null, null));
        data.put("commits", projectService.listCommitActivities());
        return R.ok(data);
    }
}

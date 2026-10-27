package com.lifedesk.english.controller;

import com.lifedesk.common.R;
import com.lifedesk.english.entity.EnglishTask;
import com.lifedesk.english.service.EnglishTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 英语提升 Controller。
 *
 * <p>路由前缀 /api/english，提供：
 * <ul>
 *   <li>GET /english/task?category=xxx&keyword=xxx     列表（可过滤+搜索）</li>
 *   <li>POST /english/task                             新建</li>
 *   <li>PUT /english/task/{id}                          编辑</li>
 *   <li>DELETE /english/task/{id}                       删除</li>
 *   <li>POST /english/task/{id}/complete                今日打卡（幂等）</li>
 *   <li>GET /english/stat                               统计</li>
 * </ul>
 * </p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/english")
@RequiredArgsConstructor
public class EnglishController {

    private final EnglishTaskService englishTaskService;

    @GetMapping("/task")
    public R<List<EnglishTask>> list(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String keyword) {
        return R.ok(englishTaskService.list(category, keyword));
    }

    @PostMapping("/task")
    public R<EnglishTask> create(@RequestBody EnglishTask task) {
        return R.ok(englishTaskService.create(task));
    }

    @PutMapping("/task/{id}")
    public R<EnglishTask> update(@PathVariable Long id, @RequestBody EnglishTask incoming) {
        return R.ok(englishTaskService.update(id, incoming));
    }

    @DeleteMapping("/task/{id}")
    public R<Void> delete(@PathVariable Long id) {
        englishTaskService.delete(id);
        return R.ok(null);
    }

    /** 今日打卡（幂等，重复点击不会重复 +1） */
    @PostMapping("/task/{id}/complete")
    public R<EnglishTask> completeToday(@PathVariable Long id) {
        return R.ok(englishTaskService.completeToday(id));
    }

    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        return R.ok(englishTaskService.getStat());
    }

    /** 看板：近 N 天每日打卡统计 + 每日目标里程碑（默认 30 天，7~90） */
    @GetMapping("/board")
    public R<Map<String, Object>> board(@RequestParam(defaultValue = "30") int days) {
        return R.ok(englishTaskService.board(days));
    }
}

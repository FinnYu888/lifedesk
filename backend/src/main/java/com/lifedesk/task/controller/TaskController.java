package com.lifedesk.task.controller;

import com.lifedesk.common.R;
import com.lifedesk.task.entity.Task;
import com.lifedesk.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务工单 Controller。
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public R<List<Task>> list(@RequestParam(required = false) String status) {
        return R.ok(taskService.listByStatus(status));
    }

    @PostMapping
    public R<Task> create(@RequestBody Task task) {
        return R.ok(taskService.create(task));
    }

    /**
     * 移动任务到新的状态列（todo / doing / done）。
     * 前端用 JSON body {"status":"doing"} 调用，避免 @RequestParam 缺省时报 500。
     */
    @PutMapping("/{id}/move")
    public R<Task> move(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newStatus = body == null ? null : body.get("status");
        return R.ok(taskService.move(id, newStatus));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(taskService.delete(id));
    }

    @GetMapping("/board")
    public R<Map<String, Object>> board() {
        return R.ok(taskService.board());
    }
}

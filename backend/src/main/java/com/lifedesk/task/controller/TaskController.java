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

    @PutMapping("/{id}/move")
    public R<Task> move(@PathVariable Long id, @RequestParam String status) {
        return R.ok(taskService.move(id, status));
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
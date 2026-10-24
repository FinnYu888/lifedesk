package com.lifedesk.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.task.entity.Task;
import com.lifedesk.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务工单 Service。
 *
 * <p>对应 06_任务工单.md 三列看板。</p>
 *
 * @author LifeDesk
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;

    public List<Task> listByStatus(String status) {
        if (status == null || status.isEmpty()) {
            return taskMapper.selectList(
                    new LambdaQueryWrapper<Task>().orderByAsc(Task::getSortNo));
        }
        return taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getStatus, status)
                        .orderByAsc(Task::getSortNo));
    }

    public Task create(Task task) {
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            throw new BizException("任务标题不能为空");
        }
        if (task.getStatus() == null) {
            task.setStatus("todo");
        }
        if (task.getPriority() == null) {
            task.setPriority("normal");
        }
        taskMapper.insert(task);
        return task;
    }

    public Task move(Long id, String newStatus) {
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new BizException(400, "缺少必填参数 status");
        }
        if (!"todo".equals(newStatus) && !"doing".equals(newStatus) && !"done".equals(newStatus)) {
            throw new BizException(400, "无效的 status: " + newStatus + "（仅支持 todo / doing / done）");
        }
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        task.setStatus(newStatus);
        taskMapper.updateById(task);
        return task;
    }

    public boolean delete(Long id) {
        return taskMapper.deleteById(id) > 0;
    }

    public Map<String, Object> board() {
        List<Task> all = taskMapper.selectList(null);
        for (Task t : all) {
            if (t.getStatus() == null) {
                t.setStatus("todo");
            }
        }
        Map<String, Object> d = new HashMap<>();
        d.put("todo", all.stream().filter(t -> "todo".equals(t.getStatus())).collect(Collectors.toList()));
        d.put("doing", all.stream().filter(t -> "doing".equals(t.getStatus())).collect(Collectors.toList()));
        d.put("done", all.stream().filter(t -> "done".equals(t.getStatus())).collect(Collectors.toList()));
        return d;
    }
}
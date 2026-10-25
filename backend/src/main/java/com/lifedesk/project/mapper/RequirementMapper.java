package com.lifedesk.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.project.entity.Requirement;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 我的需求 Mapper。
 *
 * @author LifeDesk
 */
public interface RequirementMapper extends BaseMapper<Requirement> {

    /**
     * 按 sprint 维度聚合统计：每个 sprint 的总数、已完成数、进行中数、待办数。
     *
     * <p>cancel 状态不参与进度计算（已取消的任务既不计入总数也不计入完成数）。
     * 返回字段：sprint, total, done, doing, todo。</p>
     */
    @Select({
        "SELECT sprint,",
        "       COUNT(*) AS total,",
        "       SUM(CASE WHEN task_status = 'done'  THEN 1 ELSE 0 END) AS done,",
        "       SUM(CASE WHEN task_status = 'doing' THEN 1 ELSE 0 END) AS doing,",
        "       SUM(CASE WHEN task_status = 'todo'  THEN 1 ELSE 0 END) AS todo",
        "FROM requirement",
        "WHERE deleted = 0 AND task_status <> 'cancel'",
        "GROUP BY sprint",
        "ORDER BY sprint DESC"
    })
    List<Map<String, Object>> selectSprintAgg();
}

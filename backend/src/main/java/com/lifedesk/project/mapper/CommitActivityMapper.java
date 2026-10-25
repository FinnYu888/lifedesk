package com.lifedesk.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifedesk.project.entity.CommitActivity;
import com.lifedesk.project.entity.CommitActivityVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 提交动态 Mapper。
 *
 * @author LifeDesk
 */
public interface CommitActivityMapper extends BaseMapper<CommitActivity> {

    /**
     * 取每个项目最近一条 commit（按 committed_at 倒序）。
     *
     * <p>SQL 思路：先 INNER JOIN 子查询拿到每个 project_id 的 max(committed_at)，
     * 再 LEFT JOIN project 拿项目名。子查询和主查询都用 deleted=0 过滤，
     * 确保已删除项目/已删除 commit 不参与。</p>
     */
    @Select({
        "SELECT c.id, c.project_id, c.author, c.message, c.type, c.time_ago,",
        "       c.committed_at, c.sha, c.create_time, c.update_time, c.deleted,",
        "       p.name AS project_name",
        "FROM commit_activity c",
        "INNER JOIN (",
        "    SELECT project_id, MAX(committed_at) AS max_time",
        "    FROM commit_activity",
        "    WHERE deleted = 0",
        "    GROUP BY project_id",
        ") m ON c.project_id = m.project_id AND c.committed_at = m.max_time",
        "LEFT JOIN project p ON c.project_id = p.id AND p.deleted = 0",
        "WHERE c.deleted = 0",
        "ORDER BY c.committed_at DESC"
    })
    List<CommitActivityVO> selectLatestPerProject();
}

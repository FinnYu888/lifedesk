package com.lifedesk.dailygoal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.dailygoal.entity.DailyGoal;
import com.lifedesk.dailygoal.mapper.DailyGoalMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日目标 Service。
 *
 * <p>核心语义：<b>修改每日目标 = 进入下一阶段</b>。
 * 同一 (module, scope) 的目标记录按 start_date 升序即里程碑时间线：
 * 每次目标变更生成一条新记录（start_date=当天）；当天内的重复修改原地更新，不产生重复里程碑。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyGoalService {

    private final DailyGoalMapper dailyGoalMapper;

    /**
     * 查询某模块的目标：current = 各 scope 当前生效目标；milestones = 完整里程碑历史（按日期升序）。
     */
    public Map<String, Object> list(String module) {
        if (module == null || module.trim().isEmpty()) {
            throw new BizException("module 不能为空");
        }
        List<DailyGoal> all = dailyGoalMapper.selectList(
                new LambdaQueryWrapper<DailyGoal>()
                        .eq(DailyGoal::getModule, module.trim())
                        .orderByAsc(DailyGoal::getStartDate)
                        .orderByAsc(DailyGoal::getId));

        // current：每个 scope 取 start_date 最大的一条（后端已按日期升序，遍历时覆盖）
        Map<String, DailyGoal> cur = new LinkedHashMap<>();
        for (DailyGoal g : all) {
            cur.put(g.getScope(), g);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("current", new ArrayList<>(cur.values()));
        data.put("milestones", all);
        data.put("stageCount", distinctStageCount(all));
        return data;
    }

    /** 阶段数 = 去重后的生效日期数（同一天多条视为同一阶段） */
    private int distinctStageCount(List<DailyGoal> all) {
        return (int) all.stream().map(DailyGoal::getStartDate).distinct().count();
    }

    /**
     * 保存目标（修改每日目标）。
     * <ul>
     *   <li>与当前目标值相同 → 幂等返回，不新增里程碑</li>
     *   <li>当天已改过（最新记录 start_date=今天）→ 原地更新，避免同一天刷出多个里程碑</li>
     *   <li>否则 → 新增一条 start_date=今天的记录，即新的里程碑分割点</li>
     * </ul>
     */
    public DailyGoal save(DailyGoal incoming) {
        if (incoming.getModule() == null || incoming.getModule().trim().isEmpty()) {
            throw new BizException("module 不能为空");
        }
        if (incoming.getScope() == null || incoming.getScope().trim().isEmpty()) {
            throw new BizException("scope 不能为空");
        }
        if (incoming.getGoalValue() == null || incoming.getGoalValue().trim().isEmpty()) {
            throw new BizException("目标值不能为空");
        }
        String module = incoming.getModule().trim();
        String scope = incoming.getScope().trim();
        String value = incoming.getGoalValue().trim();
        LocalDate today = LocalDate.now();

        DailyGoal latest = latest(module, scope);
        if (latest != null && latest.getGoalValue() != null && latest.getGoalValue().equals(value)) {
            return latest; // 值未变，幂等
        }
        if (latest != null && today.equals(latest.getStartDate())) {
            // 当天内修改：原地更新
            latest.setGoalValue(value);
            if (incoming.getLabel() != null) latest.setLabel(incoming.getLabel());
            if (incoming.getUnit() != null) latest.setUnit(incoming.getUnit());
            dailyGoalMapper.updateById(latest);
            log.info("[DailyGoal] 当天内修改目标 {}.{} -> {}", module, scope, value);
            return latest;
        }
        // 新里程碑
        DailyGoal g = new DailyGoal();
        g.setModule(module);
        g.setScope(scope);
        g.setLabel(incoming.getLabel());
        g.setGoalValue(value);
        g.setUnit(incoming.getUnit());
        g.setStartDate(today);
        dailyGoalMapper.insert(g);
        log.info("[DailyGoal] 新里程碑：{}.{} 从 {} 起目标 = {}", module, scope, today, value);
        return g;
    }

    /** 某模块某 scope 当前生效目标（start_date 最大），无则 null */
    public DailyGoal latest(String module, String scope) {
        List<DailyGoal> list = dailyGoalMapper.selectList(
                new LambdaQueryWrapper<DailyGoal>()
                        .eq(DailyGoal::getModule, module)
                        .eq(DailyGoal::getScope, scope)
                        .orderByDesc(DailyGoal::getStartDate)
                        .orderByDesc(DailyGoal::getId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}

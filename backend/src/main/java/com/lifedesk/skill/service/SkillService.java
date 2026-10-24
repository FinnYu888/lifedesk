package com.lifedesk.skill.service;

import com.lifedesk.common.BizException;
import com.lifedesk.skill.entity.Skill;
import com.lifedesk.skill.mapper.SkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skills 工坊 Service。
 *
 * <p>对应 04_Skills工坊.md：技能成就统计 + 5 张技能卡。</p>
 *
 * @author LifeDesk
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillMapper skillMapper;

    /** 统计卡：技能成就 5 / 需提升 2 / 已掌握 1 */
    public Map<String, Object> getStat() {
        List<Skill> all = skillMapper.selectList(null);
        long mastered = all.stream().filter(s -> s.getLevel() != null && s.getLevel() >= 5).count();
        long improving = all.stream().filter(s -> s.getLevel() != null && (s.getLevel() == 3 || s.getLevel() == 4)).count();
        Map<String, Object> stat = new HashMap<>();
        stat.put("total_skills", all.size());
        stat.put("improving", improving);
        stat.put("mastered", mastered);
        return stat;
    }

    public List<Skill> list() {
        return skillMapper.selectList(null);
    }

    public Skill create(Skill skill) {
        if (skill.getName() == null || skill.getName().trim().isEmpty()) {
            throw new BizException("技能名不能为空");
        }
        // 重名校验
        Long existCount = skillMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Skill>()
                        .eq(Skill::getName, skill.getName()));
        if (existCount != null && existCount > 0) {
            throw new BizException("该技能已存在");
        }
        if (skill.getLevel() == null) {
            skill.setLevel(1);
        }
        if (skill.getProgress() == null) {
            skill.setProgress(0);
        }
        skillMapper.insert(skill);
        return skill;
    }
}
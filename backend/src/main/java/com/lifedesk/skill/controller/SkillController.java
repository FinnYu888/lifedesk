package com.lifedesk.skill.controller;

import com.lifedesk.common.R;
import com.lifedesk.skill.entity.Skill;
import com.lifedesk.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Skills 工坊 Controller。
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        return R.ok(skillService.getStat());
    }

    @GetMapping
    public R<List<Skill>> list() {
        return R.ok(skillService.list());
    }

    @PostMapping
    public R<Skill> create(@RequestBody Skill skill) {
        return R.ok(skillService.create(skill));
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> d = new java.util.HashMap<>();
        d.put("stat", skillService.getStat());
        d.put("skills", skillService.list());
        return R.ok(d);
    }
}
package com.lifedesk.favorite.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.favorite.entity.Favorite;
import com.lifedesk.favorite.mapper.FavoriteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏与笔记 Service。
 *
 * <p>type=link 表示收藏网站；type=note 表示笔记。
 * 两者共用同一张表，按 type 区分；name 是名称（网站名/笔记标题），value 是值（URL/笔记正文）。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    /**
     * 列表查询。
     *
     * @param type    可选 link/note，null 时返回全部
     * @param keyword 可选，按 name 或 value 模糊匹配
     */
    public List<Favorite> list(String type, String keyword) {
        QueryWrapper<Favorite> qw = new QueryWrapper<>();
        if (type != null && !type.trim().isEmpty()) {
            qw.eq("type", type.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("name", kw).or().like("value", kw).or().like("description", kw));
        }
        qw.orderByDesc("update_time");
        return favoriteMapper.selectList(qw);
    }

    public Favorite create(Favorite favorite) {
        normalizeType(favorite);
        if (favorite.getName() == null || favorite.getName().trim().isEmpty()) {
            throw new BizException("名称不能为空");
        }
        if (favorite.getValue() == null || favorite.getValue().trim().isEmpty()) {
            throw new BizException("值不能为空");
        }
        favoriteMapper.insert(favorite);
        return favorite;
    }

    public Favorite update(Long id, Favorite incoming) {
        Favorite existed = favoriteMapper.selectById(id);
        if (existed == null) {
            throw new BizException("记录不存在或已被删除");
        }
        if (incoming.getName() != null && !incoming.getName().trim().isEmpty()) {
            existed.setName(incoming.getName());
        }
        if (incoming.getValue() != null && !incoming.getValue().trim().isEmpty()) {
            existed.setValue(incoming.getValue());
        }
        if (incoming.getType() != null && !incoming.getType().trim().isEmpty()) {
            existed.setType(incoming.getType().trim());
        }
        // description 留空表示"清空备注"，所以接收 null 也写入
        existed.setDescription(incoming.getDescription());
        favoriteMapper.updateById(existed);
        return existed;
    }

    public void delete(Long id) {
        Favorite existed = favoriteMapper.selectById(id);
        if (existed == null) {
            throw new BizException("记录不存在或已被删除");
        }
        favoriteMapper.deleteById(id);
    }

    /** 统计：返回收藏总数与笔记总数 */
    public Map<String, Object> getStat() {
        Long total = favoriteMapper.selectCount(null);
        Long links = favoriteMapper.selectCount(new QueryWrapper<Favorite>().eq("type", "link"));
        Long notes = favoriteMapper.selectCount(new QueryWrapper<Favorite>().eq("type", "note"));
        Map<String, Object> stat = new HashMap<>();
        stat.put("total", total);
        stat.put("links", links);
        stat.put("notes", notes);
        return stat;
    }

    /** 规范化 type 字段，未传或传错值时默认 link */
    private void normalizeType(Favorite favorite) {
        String t = favorite.getType();
        if (t == null || t.trim().isEmpty() || (!"link".equals(t.trim()) && !"note".equals(t.trim()))) {
            favorite.setType("link");
        } else {
            favorite.setType(t.trim());
        }
    }
}

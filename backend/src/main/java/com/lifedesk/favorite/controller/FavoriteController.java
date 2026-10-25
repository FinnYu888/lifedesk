package com.lifedesk.favorite.controller;

import com.lifedesk.common.R;
import com.lifedesk.favorite.entity.Favorite;
import com.lifedesk.favorite.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 收藏与笔记 Controller。
 *
 * <p>路由前缀 /api/favorite，提供：
 * <ul>
 *   <li>GET /favorite?type=link|note&keyword=xxx  列表（可过滤+搜索）</li>
 *   <li>POST /favorite                            新建</li>
 *   <li>PUT /favorite/{id}                        编辑</li>
 *   <li>DELETE /favorite/{id}                     删除</li>
 *   <li>GET /favorite/stat                        统计（总数/收藏数/笔记数）</li>
 * </ul>
 * </p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 列表：type=link|note 可选；keyword 模糊匹配 name/value/description */
    @GetMapping
    public R<List<Favorite>> list(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) String keyword) {
        return R.ok(favoriteService.list(type, keyword));
    }

    @PostMapping
    public R<Favorite> create(@RequestBody Favorite favorite) {
        return R.ok(favoriteService.create(favorite));
    }

    @PutMapping("/{id}")
    public R<Favorite> update(@PathVariable Long id, @RequestBody Favorite incoming) {
        return R.ok(favoriteService.update(id, incoming));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        favoriteService.delete(id);
        return R.ok(null);
    }

    @GetMapping("/stat")
    public R<Map<String, Object>> stat() {
        return R.ok(favoriteService.getStat());
    }
}

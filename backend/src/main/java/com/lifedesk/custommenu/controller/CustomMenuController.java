package com.lifedesk.custommenu.controller;

import com.lifedesk.common.R;
import com.lifedesk.custommenu.entity.CustomMenu;
import com.lifedesk.custommenu.service.CustomMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自定义菜单 Controller。
 *
 * <p>路由前缀 /api/custom-menu，提供：
 * <ul>
 *   <li>GET /custom-menu?keyword=xxx   列表（按 sort_no 升序，可模糊搜索 name/url）</li>
 *   <li>POST /custom-menu               新建（name + url 必填）</li>
 *   <li>PUT /custom-menu/{id}           编辑</li>
 *   <li>DELETE /custom-menu/{id}        删除</li>
 * </ul>
 * </p>
 *
 * <p>用途：用户在侧栏自行挂载外部网站（如 RPT 接口测试用例平台），
 * 前端拿到列表后渲染成侧栏菜单项，点击后通过 iframe 把 url 嵌入到当前系统展示。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/custom-menu")
@RequiredArgsConstructor
public class CustomMenuController {

    private final CustomMenuService customMenuService;

    /** 列表：keyword 可选，按 name / url 模糊匹配 */
    @GetMapping
    public R<List<CustomMenu>> list(@RequestParam(required = false) String keyword) {
        return R.ok(customMenuService.list(keyword));
    }

    @PostMapping
    public R<CustomMenu> create(@RequestBody CustomMenu menu) {
        return R.ok(customMenuService.create(menu));
    }

    @PutMapping("/{id}")
    public R<CustomMenu> update(@PathVariable Long id, @RequestBody CustomMenu incoming) {
        return R.ok(customMenuService.update(id, incoming));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        customMenuService.delete(id);
        return R.ok(null);
    }
}

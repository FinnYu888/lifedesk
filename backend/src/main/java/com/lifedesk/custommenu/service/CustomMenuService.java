package com.lifedesk.custommenu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.custommenu.entity.CustomMenu;
import com.lifedesk.custommenu.mapper.CustomMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自定义菜单 Service。
 *
 * <p>管理用户在侧栏自行添加的"自定义菜单"——每项挂一个外部网站地址，
 * 前端通过 iframe 把该网站嵌入到 LifeDesk 当前系统内展示（不跳转新窗口）。</p>
 *
 * <p>列表默认按 sort_no 升序、再按 id 升序排列，保证用户自定义的顺序稳定。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomMenuService {

    private final CustomMenuMapper customMenuMapper;

    /**
     * 列表查询：返回所有自定义菜单，按 sort_no 升序、id 升序。
     *
     * @param keyword 可选，按 name / url 模糊匹配
     */
    public List<CustomMenu> list(String keyword) {
        QueryWrapper<CustomMenu> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("name", kw).or().like("url", kw));
        }
        qw.orderByAsc("sort_no", "id");
        return customMenuMapper.selectList(qw);
    }

    public CustomMenu create(CustomMenu menu) {
        if (menu.getName() == null || menu.getName().trim().isEmpty()) {
            throw new BizException("菜单名不能为空");
        }
        if (menu.getUrl() == null || menu.getUrl().trim().isEmpty()) {
            throw new BizException("网站地址不能为空");
        }
        // sortNo 缺省 0，避免前端漏传时排序异常
        if (menu.getSortNo() == null) {
            menu.setSortNo(0);
        }
        customMenuMapper.insert(menu);
        return menu;
    }

    public CustomMenu update(Long id, CustomMenu incoming) {
        CustomMenu existed = customMenuMapper.selectById(id);
        if (existed == null) {
            throw new BizException("菜单不存在或已被删除");
        }
        if (incoming.getName() != null && !incoming.getName().trim().isEmpty()) {
            existed.setName(incoming.getName());
        }
        if (incoming.getUrl() != null && !incoming.getUrl().trim().isEmpty()) {
            existed.setUrl(incoming.getUrl());
        }
        if (incoming.getSortNo() != null) {
            existed.setSortNo(incoming.getSortNo());
        }
        // icon 允许清空（传 null 也写入），保持与前端意图一致
        existed.setIcon(incoming.getIcon());
        customMenuMapper.updateById(existed);
        return existed;
    }

    public void delete(Long id) {
        CustomMenu existed = customMenuMapper.selectById(id);
        if (existed == null) {
            throw new BizException("菜单不存在或已被删除");
        }
        customMenuMapper.deleteById(id);
    }
}

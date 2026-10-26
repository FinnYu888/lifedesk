package com.lifedesk.custommenu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义菜单。
 *
 * <p>用户在侧边栏自行添加的"自定义菜单"项，每项关联一个外部网站地址 url。
 * 点击菜单后，前端通过 iframe 把 url 嵌入到当前系统内展示（不跳转新窗口）。</p>
 *
 * <p>典型用例：把 RPT 接口测试用例平台（http://10.139.92.114:8098/#/login）
 * 这种内部系统作为菜单挂到 LifeDesk 侧栏，统一在一个工作台访问。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("custom_menu")
public class CustomMenu extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 菜单名（显示在侧栏，如 "RPT 接口测试用例平台"） */
    private String name;

    /** 网站地址（点击菜单后用 iframe 嵌入到当前系统展示，如 http://10.139.92.114:8098/#/login） */
    private String url;

    /** 排序号，越小越靠前，默认 0 */
    private Integer sortNo;

    /** 图标枚举（前端 Element Plus 图标名，可选，缺省用 Link） */
    private String icon;
}

package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 食物字典。
 *
 * <p>提供每 100g 卡路里，前端选择后 POST /health/diet 由后端计算
 * calories_intake = kcal_per_100g × amount_g / 100。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("food_dict")
public class FoodDict extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 食物名 */
    private String name;

    /** 类别：主食/肉蛋/蔬菜/水果/饮品/零食 */
    private String category;

    /** 每 100g 卡路里 */
    private Double kcalPer100g;

    /** 默认份量 g/ml */
    private Integer defaultAmount;

    /** 默认单位 g/ml/份 */
    private String defaultUnit;
}

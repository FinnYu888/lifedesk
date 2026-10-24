package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 饮食摄入记录。
 *
 * <p>每条 = 食物 × 量 × 餐次，calories_intake 由后端按食物字典自动算。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("diet_record")
public class DietRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 food_dict.id */
    private Long foodId;

    /** 食物名冗余，便于列表展示 */
    private String foodName;

    /** 餐次：breakfast/lunch/dinner/snack */
    private String meal;

    /** 摄入量 g/ml */
    private Double amountG;

    /** 摄入 kcal，后端自动计算 */
    private Double caloriesIntake;

    private LocalDate recordDate;
}

package com.lifedesk.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运动动作字典。
 *
 * <p>提供部位、MET、每组消耗等基础数据，前端选择后 POST /health/workout
 * 由后端计算 calories_burned。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exercise_dict")
public class ExerciseDict extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 动作名：卧推/深蹲/硬拉 */
    private String name;

    /** 部位：胸/背/腿/核心/肩臂/有氧 */
    private String part;

    /** MET 代谢当量，用于时长模式计算 */
    private Double met;

    /** 每组默认消耗 kcal，用于组数模式 */
    private Double kcalPerSet;

    /** 图标枚举 */
    private String icon;
}

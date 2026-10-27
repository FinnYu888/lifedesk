package com.lifedesk.nativeapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自定义本地应用。
 *
 * <p>用户配置本地应用（exe/lnk 等）的启动路径，点击后由后端在本机真实启动，
 * 系统内通过窗口截图流嵌入展示应用画面，交互在原生窗口进行（功能不变）。</p>
 *
 * @author LifeDesk
 */
@Data
@TableName("native_app")
public class NativeApp {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 展示名 */
    private String name;

    /** 本地应用启动路径（exe/lnk 等） */
    private String appPath;

    /** 启动参数（可选） */
    private String args;

    /** 工作目录（可选，默认取应用所在目录） */
    private String workDir;

    /** 排序 */
    private Integer sortOrder;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

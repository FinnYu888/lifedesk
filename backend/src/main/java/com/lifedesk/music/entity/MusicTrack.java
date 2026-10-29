package com.lifedesk.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifedesk.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 音乐曲目。
 *
 * <p>「我的音乐」页面数据：支持外链与上传源文件两种来源，全局播放器播放时
 * 不随菜单切换而停止。</p>
 *
 * @author LifeDesk
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("music_track")
public class MusicTrack extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌曲名 */
    private String title;

    /** 歌手 */
    private String artist;

    /** 来源：link=外链 / upload=上传源文件 */
    private String sourceType;

    /** 外链地址（source_type=link 时有效） */
    private String url;

    /** 上传文件相对路径（source_type=upload 时有效，相对 upload-dir） */
    private String filePath;

    /** 文件大小（字节，上传时记录） */
    private Long fileSize;

    /** 时长（秒，可选） */
    private Integer durationSec;

    /** 歌词（LRC 同步格式或纯文本；来自 LRCLIB 开放歌词库或手动录入） */
    private String lyrics;
}

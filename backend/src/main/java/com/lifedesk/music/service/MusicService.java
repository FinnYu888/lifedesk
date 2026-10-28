package com.lifedesk.music.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.music.entity.MusicTrack;
import com.lifedesk.music.mapper.MusicTrackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 我的音乐 Service：新增（链接/上传）、删除、列表、文件存储。
 *
 * <p>上传文件统一存放于 {@code music.upload-dir}（默认 {@code ./data/music}），
 * 文件名用 UUID 重写避免冲突/路径穿越；删除曲目时同步删除源文件。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {

    /** 允许上传的音频扩展名（Java 8：无 Set.of） */
    private static final Set<String> AUDIO_EXT = new java.util.HashSet<>(java.util.Arrays.asList(
            "mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "ape", "opus", "webm"));

    private final MusicTrackMapper musicTrackMapper;

    /** 上传文件存储目录（相对后端工作目录） */
    @Value("${music.upload-dir:data/music}")
    private String uploadDir;

    /** 曲目列表（新增倒序） */
    public List<MusicTrack> list() {
        return musicTrackMapper.selectList(
                new QueryWrapper<MusicTrack>().orderByDesc("create_time"));
    }

    /** 按 ID 取曲目 */
    public MusicTrack get(Long id) {
        MusicTrack track = musicTrackMapper.selectById(id);
        if (track == null) {
            throw new BizException("曲目不存在或已被删除");
        }
        return track;
    }

    /** 新增外链曲目 */
    public MusicTrack addLink(MusicTrack track) {
        if (track.getTitle() == null || track.getTitle().trim().isEmpty()) {
            throw new BizException("歌曲名不能为空");
        }
        if (track.getUrl() == null || track.getUrl().trim().isEmpty()) {
            throw new BizException("音乐链接不能为空");
        }
        track.setTitle(track.getTitle().trim());
        track.setSourceType("link");
        track.setUrl(track.getUrl().trim());
        track.setFilePath(null);
        track.setFileSize(null);
        musicTrackMapper.insert(track);
        return track;
    }

    /** 上传音乐源文件新增曲目 */
    public MusicTrack upload(MultipartFile file, String title, String artist) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择音乐文件");
        }
        String original = file.getOriginalFilename() == null ? "audio" : file.getOriginalFilename();
        String ext = extOf(original);
        if (!AUDIO_EXT.contains(ext)) {
            throw new BizException("不支持的音频格式：" + ext + "（支持 " + AUDIO_EXT + "）");
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            // UUID 重写文件名，避免冲突与路径穿越
            String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(stored);
            java.io.InputStream in = file.getInputStream();
            try {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                in.close();
            }

            MusicTrack track = new MusicTrack();
            track.setTitle(title != null && !title.trim().isEmpty()
                    ? title.trim() : stripExt(original));
            track.setArtist(artist != null && !artist.trim().isEmpty() ? artist.trim() : null);
            track.setSourceType("upload");
            track.setUrl(null);
            track.setFilePath(stored);
            track.setFileSize(file.getSize());
            musicTrackMapper.insert(track);
            log.info("[Music] 上传完成：{} ({} 字节) -> {}", track.getTitle(), file.getSize(), target);
            return track;
        } catch (IOException e) {
            log.error("[Music] 文件保存失败", e);
            throw new BizException("文件保存失败：" + e.getMessage());
        }
    }

    /** 删除曲目（软删；上传的源文件同步物理删除） */
    public void delete(Long id) {
        MusicTrack track = musicTrackMapper.selectById(id);
        if (track == null) {
            throw new BizException("曲目不存在或已被删除");
        }
        if ("upload".equals(track.getSourceType()) && track.getFilePath() != null) {
            try {
                Path target = Paths.get(uploadDir).toAbsolutePath().normalize()
                        .resolve(track.getFilePath());
                Files.deleteIfExists(target);
            } catch (IOException e) {
                log.warn("[Music] 删除源文件失败：{}", track.getFilePath());
            }
        }
        musicTrackMapper.deleteById(id);
    }

    /** 解析上传文件的绝对路径（Controller 流式输出用） */
    public Path resolveFile(MusicTrack track) {
        if (!"upload".equals(track.getSourceType()) || track.getFilePath() == null) {
            throw new BizException("该曲目不是上传文件");
        }
        Path p = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(track.getFilePath());
        if (!Files.exists(p)) {
            throw new BizException("源文件已丢失，请删除后重新上传");
        }
        return p;
    }

    /** 回填时长（前端播放器 loadedmetadata 后调用） */
    public void updateDuration(Long id, Integer durationSec) {
        MusicTrack track = musicTrackMapper.selectById(id);
        if (track != null && durationSec != null && durationSec > 0) {
            track.setDurationSec(durationSec);
            musicTrackMapper.updateById(track);
        }
    }

    /** 按扩展名猜 Content-Type */
    public String contentTypeOf(String fileName) {
        String ext = extOf(fileName);
        switch (ext) {
            case "mp3": return "audio/mpeg";
            case "wav": return "audio/wav";
            case "ogg":
            case "opus": return "audio/ogg";
            case "m4a": return "audio/mp4";
            case "aac": return "audio/aac";
            case "flac": return "audio/flac";
            case "webm": return "audio/webm";
            default: return "application/octet-stream";
        }
    }

    private String extOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

package com.lifedesk.music.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 我的音乐 Service：新增（链接/上传）、删除、列表、文件存储、歌词（LRCLIB）。
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

    /** LRCLIB 响应解析 */
    private static final ObjectMapper JSON = new ObjectMapper();

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

    /**
     * 拉取歌词：优先返回已存歌词；否则从 LRCLIB 开放歌词库按歌名/歌手搜索，
     * 优先带时间轴的 LRC 同步歌词，取到后入库并返回。返回 {lyrics, synced}。
     */
    public Map<String, Object> fetchLyrics(Long id) {
        MusicTrack track = musicTrackMapper.selectById(id);
        if (track == null) {
            throw new BizException("曲目不存在或已被删除");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (track.getLyrics() != null && !track.getLyrics().trim().isEmpty()) {
            result.put("lyrics", track.getLyrics());
            result.put("synced", track.getLyrics().contains("["));
            return result;
        }
        String lyrics = searchLrclib(track.getTitle(), track.getArtist());
        if (lyrics == null || lyrics.isEmpty()) {
            throw new BizException("未在歌词库中找到「" + track.getTitle() + "」的歌词，可手动粘贴");
        }
        track.setLyrics(lyrics);
        musicTrackMapper.updateById(track);
        result.put("lyrics", lyrics);
        result.put("synced", lyrics.contains("["));
        return result;
    }

    /** 手动保存/编辑歌词 */
    public void saveLyrics(Long id, String lyrics) {
        MusicTrack track = musicTrackMapper.selectById(id);
        if (track == null) {
            throw new BizException("曲目不存在或已被删除");
        }
        track.setLyrics(lyrics == null || lyrics.trim().isEmpty() ? null : lyrics.trim());
        musicTrackMapper.updateById(track);
    }

    /**
     * LRCLIB 搜索：https://lrclib.net/api/search?track_name=&artist_name=
     * 返回数组，优先取带 syncedLyrics 的条目，其次 plainLyrics。
     */
    private String searchLrclib(String title, String artist) {
        try {
            String q = "https://lrclib.net/api/search?track_name="
                    + java.net.URLEncoder.encode(title == null ? "" : title, "UTF-8");
            if (artist != null && !artist.trim().isEmpty()) {
                q += "&artist_name=" + java.net.URLEncoder.encode(artist.trim(), "UTF-8");
            }
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(q).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "LifeDesk/1.0 (https://github.com/FinnYu888/lifedesk)");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("[Music] LRCLIB 搜索失败 HTTP {}: {}", code, title);
                return null;
            }
            java.io.InputStream in = conn.getInputStream();
            JsonNode arr;
            try {
                java.io.InputStreamReader reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
                arr = JSON.readTree(reader);
            } finally {
                in.close();
            }
            if (arr == null || !arr.isArray() || arr.size() == 0) {
                return null;
            }
            String plainFallback = null;
            for (JsonNode o : arr) {
                if (o.has("syncedLyrics") && o.get("syncedLyrics").asText("").length() > 0) {
                    return o.get("syncedLyrics").asText();
                }
                if (plainFallback == null && o.has("plainLyrics") && o.get("plainLyrics").asText("").length() > 0) {
                    plainFallback = o.get("plainLyrics").asText();
                }
            }
            return plainFallback;
        } catch (Exception e) {
            log.warn("[Music] LRCLIB 歌词搜索异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 替换曲目音频源文件：保留歌名/歌手等元信息，仅更换音频文件。
     *
     * <p>用于把外链/占位曲目升级为完整歌曲：上传新文件成功落盘后，
     * 再删除旧文件（upload 类型）并更新记录指向新文件。</p>
     */
    public MusicTrack replaceFile(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择音乐文件");
        }
        MusicTrack track = musicTrackMapper.selectById(id);
        if (track == null) {
            throw new BizException("曲目不存在或已被删除");
        }
        String original = file.getOriginalFilename() == null ? "audio" : file.getOriginalFilename();
        String ext = extOf(original);
        if (!AUDIO_EXT.contains(ext)) {
            throw new BizException("不支持的音频格式：" + ext + "（支持 " + AUDIO_EXT + "）");
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(stored);
            java.io.InputStream in = file.getInputStream();
            try {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                in.close();
            }

            String oldFile = "upload".equals(track.getSourceType()) ? track.getFilePath() : null;
            track.setSourceType("upload");
            track.setUrl(null);
            track.setFilePath(stored);
            track.setFileSize(file.getSize());
            musicTrackMapper.updateById(track);

            // 新文件已生效，清理旧文件
            if (oldFile != null && !oldFile.equals(stored)) {
                try {
                    Files.deleteIfExists(dir.resolve(oldFile));
                } catch (IOException e) {
                    log.warn("[Music] 替换后删除旧文件失败：{}", oldFile);
                }
            }
            log.info("[Music] 替换音频完成：{} ({} 字节) -> {}", track.getTitle(), file.getSize(), target);
            return track;
        } catch (IOException e) {
            log.error("[Music] 替换音频保存文件失败", e);
            throw new BizException("文件保存失败：" + e.getMessage());
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

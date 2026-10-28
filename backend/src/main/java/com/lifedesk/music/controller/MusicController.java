package com.lifedesk.music.controller;

import com.lifedesk.common.R;
import com.lifedesk.music.entity.MusicTrack;
import com.lifedesk.music.service.MusicService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 我的音乐 Controller。
 *
 * <p>接口前缀 /api/music。支持外链/上传两种新增方式；上传文件经
 * {@code GET /{id}/file} 流式播放，全局播放器在菜单切换时不中断。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    /** 曲目列表（新增倒序） */
    @GetMapping("/list")
    public R<List<MusicTrack>> list() {
        return R.ok(musicService.list());
    }

    /** 新增外链曲目：body {title, artist?, url} */
    @PostMapping
    public R<MusicTrack> addLink(@RequestBody MusicTrack track) {
        return R.ok(musicService.addLink(track));
    }

    /** 上传音乐源文件：multipart file + 可选 title/artist */
    @PostMapping("/upload")
    public R<MusicTrack> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "title", required = false) String title,
                                @RequestParam(value = "artist", required = false) String artist) {
        return R.ok(musicService.upload(file, title, artist));
    }

    /** 删除曲目（软删 + 上传源文件物理删除） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        musicService.delete(id);
        return R.ok();
    }

    /** 流式输出上传的音频文件（全局播放器播放地址） */
    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> file(@PathVariable Long id) throws IOException {
        MusicTrack track = musicService.get(id);
        Path path = musicService.resolveFile(track);
        InputStream in = Files.newInputStream(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(musicService.contentTypeOf(path.getFileName().toString())));
        headers.setContentLength(Files.size(path));
        headers.set("Accept-Ranges", "bytes");
        return new ResponseEntity<>(new InputStreamResource(in), headers, org.springframework.http.HttpStatus.OK);
    }

    /** 回填曲目时长（秒）：body {durationSec} */
    @PostMapping("/{id}/duration")
    public R<Void> updateDuration(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        musicService.updateDuration(id, body == null ? null : body.get("durationSec"));
        return R.ok();
    }
}

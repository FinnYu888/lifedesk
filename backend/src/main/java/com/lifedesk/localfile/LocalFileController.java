package com.lifedesk.localfile;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地图片文件代理 Controller。
 *
 * <p>背景：前端运行在 http://localhost:8081，浏览器的同源安全策略禁止 http 页面
 * 直接用 file:/// 协议加载本地图片。收藏卡片的截图想支持本地图片路径
 * （如 C:\Users\me\Pictures\login.png），必须由后端读取文件并以 HTTP 流返回。</p>
 *
 * <p>路由：GET /api/local-file?path=本地图片绝对路径
 * （context-path 已是 /api，这里映射 /local-file）</p>
 *
 * <p>安全约束（内网个人工具定位）：
 * <ul>
 *   <li>仅允许图片扩展名白名单（png/jpg/jpeg/gif/webp/bmp/svg/ico），杜绝任意文件读取</li>
 *   <li>仅允许绝对路径，拒绝相对路径穿越</li>
 *   <li>文件大小上限 20MB，防止误填大文件拖垮内存</li>
 * </ul></p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/local-file")
public class LocalFileController {

    /** 允许的图片扩展名 → MIME 映射（Java 8 兼容：静态块构建） */
    private static final Map<String, MediaType> MEDIA_TYPES;
    private static final Set<String> ALLOWED_EXT;

    static {
        Map<String, MediaType> m = new java.util.HashMap<>();
        m.put("png", MediaType.IMAGE_PNG);
        m.put("jpg", MediaType.IMAGE_JPEG);
        m.put("jpeg", MediaType.IMAGE_JPEG);
        m.put("gif", MediaType.IMAGE_GIF);
        m.put("webp", MediaType.parseMediaType("image/webp"));
        m.put("bmp", MediaType.parseMediaType("image/bmp"));
        m.put("svg", MediaType.parseMediaType("image/svg+xml"));
        m.put("ico", MediaType.parseMediaType("image/x-icon"));
        MEDIA_TYPES = java.util.Collections.unmodifiableMap(m);
        ALLOWED_EXT = java.util.Collections.unmodifiableSet(m.keySet());
    }

    /** 单文件大小上限 20MB */
    private static final long MAX_SIZE = 20L * 1024 * 1024;

    @GetMapping
    public ResponseEntity<byte[]> serve(@RequestParam("path") String path) {
        if (path == null || path.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("path is required".getBytes());
        }
        Path p = Paths.get(path);
        // 仅允许绝对路径，防相对路径穿越
        if (!p.isAbsolute()) {
            return ResponseEntity.badRequest().body("absolute path required".getBytes());
        }
        String ext = extOf(p.getFileName().toString());
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            return ResponseEntity.badRequest()
                    .body(("unsupported image type, allowed: " + ALLOWED_EXT).getBytes());
        }
        if (!Files.isRegularFile(p)) {
            return ResponseEntity.notFound().build();
        }
        try {
            long size = Files.size(p);
            if (size > MAX_SIZE) {
                return ResponseEntity.status(413)
                        .body(("file too large (max 20MB): " + size).getBytes());
            }
            byte[] data = Files.readAllBytes(p);
            // 前端按图片 URL 引用，缓存 1 小时减少重复读取
            return ResponseEntity.ok()
                    .contentType(MEDIA_TYPES.get(ext))
                    .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)))
                    .body(data);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(("read failed: " + e.getMessage()).getBytes());
        }
    }

    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

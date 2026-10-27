package com.lifedesk.nativeapp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.nativeapp.entity.NativeApp;
import com.lifedesk.nativeapp.mapper.NativeAppMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 常用App（自定义本地应用）Service。
 *
 * <p>点击应用由后端在本机真实启动（ProcessBuilder，原应用功能完全不变），
 * 不做窗口嵌入。展示图标与桌面快捷方式一致：通过 Windows Shell 提取
 * 系统为该文件渲染的图标（exe 用自身图标，lnk 用目标程序图标）。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
public class NativeAppService {

    private final NativeAppMapper nativeAppMapper;

    /** 应用图标缓存：appId -> PNG 字节（编辑/删除时失效） */
    private final Map<Long, byte[]> iconCache = new ConcurrentHashMap<Long, byte[]>();

    public NativeAppService(NativeAppMapper nativeAppMapper) {
        this.nativeAppMapper = nativeAppMapper;
    }

    // ===================== CRUD =====================

    public List<NativeApp> list() {
        return nativeAppMapper.selectList(new QueryWrapper<NativeApp>().orderByAsc("sort_order").orderByAsc("id"));
    }

    public NativeApp create(NativeApp app) {
        validate(app);
        app.setId(null);
        if (app.getSortOrder() == null) app.setSortOrder(0);
        nativeAppMapper.insert(app);
        log.info("[NativeApp] 新增应用 {} -> {}", app.getName(), app.getAppPath());
        return app;
    }

    public NativeApp update(Long id, NativeApp incoming) {
        NativeApp existed = requireApp(id);
        validate(incoming);
        existed.setName(incoming.getName());
        existed.setAppPath(incoming.getAppPath());
        existed.setArgs(incoming.getArgs());
        existed.setWorkDir(incoming.getWorkDir());
        if (incoming.getSortOrder() != null) existed.setSortOrder(incoming.getSortOrder());
        nativeAppMapper.updateById(existed);
        iconCache.remove(id);
        return existed;
    }

    public void delete(Long id) {
        requireApp(id);
        nativeAppMapper.deleteById(id);
        iconCache.remove(id);
    }

    private NativeApp requireApp(Long id) {
        NativeApp app = nativeAppMapper.selectById(id);
        if (app == null) {
            throw new BizException("应用不存在或已被删除");
        }
        return app;
    }

    private void validate(NativeApp app) {
        if (app.getName() == null || app.getName().trim().isEmpty()) {
            throw new BizException("应用名称不能为空");
        }
        if (app.getAppPath() == null || app.getAppPath().trim().isEmpty()) {
            throw new BizException("应用启动路径不能为空");
        }
    }

    // ===================== 启动 =====================

    /**
     * 启动本地应用（真实启动，不做窗口管理）。
     * .lnk/.url/.bat/.cmd 等需要 shell 语义的路径走 cmd start，普通 exe 直接 exec。
     */
    public Map<String, Object> launch(Long id) {
        NativeApp app = requireApp(id);

        String path = app.getAppPath().trim();
        String lower = path.toLowerCase();
        File file = new File(path);
        if (!file.exists()) {
            throw new BizException("启动路径不存在：" + path);
        }

        List<String> cmd = new ArrayList<String>();
        if (lower.endsWith(".lnk") || lower.endsWith(".url") || lower.endsWith(".bat") || lower.endsWith(".cmd")) {
            // shell 语义路径：cmd /c start "" "path" [args]
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add("start");
            cmd.add("");
            cmd.add(path);
            if (app.getArgs() != null && !app.getArgs().trim().isEmpty()) {
                cmd.add(app.getArgs().trim());
            }
        } else {
            cmd.add(path);
            if (app.getArgs() != null && !app.getArgs().trim().isEmpty()) {
                for (String a : app.getArgs().trim().split("\\s+")) {
                    cmd.add(a);
                }
            }
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (app.getWorkDir() != null && !app.getWorkDir().trim().isEmpty()) {
            pb.directory(new File(app.getWorkDir().trim()));
        } else if (file.getParentFile() != null && file.getParentFile().isDirectory()) {
            pb.directory(file.getParentFile());
        }

        try {
            pb.start();
            log.info("[NativeApp] 启动应用 {} 成功", app.getName());
            Map<String, Object> r = new HashMap<String, Object>();
            r.put("launched", true);
            return r;
        } catch (Exception e) {
            log.error("[NativeApp] 启动失败 {}：{}", path, e.getMessage());
            throw new BizException("启动失败：" + e.getMessage());
        }
    }

    // ===================== 应用图标（与桌面快捷方式一致） =====================

    /**
     * 提取应用图标，返回 PNG 字节。
     * 使用 Windows Shell 为该文件渲染的图标（exe 取自身图标，lnk 取目标程序图标），
     * 视觉上与桌面快捷方式完全一致。结果按 appId 缓存。
     */
    public byte[] icon(Long id) {
        NativeApp app = requireApp(id);
        byte[] cached = iconCache.get(id);
        if (cached != null) {
            return cached;
        }
        byte[] png = extractIconPng(app.getAppPath().trim());
        iconCache.put(id, png);
        return png;
    }

    private byte[] extractIconPng(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new BizException("文件不存在：" + path);
        }
        // 优先 ShellFolder 大图标（32px，与桌面快捷方式同源），失败降级文件选择器系统图标
        Icon icon = null;
        try {
            sun.awt.shell.ShellFolder sf = sun.awt.shell.ShellFolder.getShellFolder(file);
            java.awt.Image image = sf.getIcon(true);
            if (image != null) {
                icon = new ImageIcon(image);
            }
        } catch (Exception ignore) {
            /* 降级 */
        }
        if (icon == null) {
            try {
                icon = FileSystemView.getFileSystemView().getSystemIcon(file);
            } catch (Exception ignore) {
                icon = null;
            }
        }
        if (icon == null) {
            throw new BizException("无法获取应用图标");
        }

        int w = Math.max(icon.getIconWidth(), 1);
        int h = Math.max(icon.getIconHeight(), 1);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            if (icon instanceof ImageIcon) {
                // 确保图像已加载完毕再绘制
                ((ImageIcon) icon).getImage();
            }
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new BizException("图标编码失败：" + e.getMessage());
        }
    }

    // ===================== 目录浏览（选择本地应用路径用） =====================

    /**
     * 浏览本地目录：dir 为空时返回盘符列表。
     * 只返回目录 + 可执行文件（exe/lnk/bat/cmd/url/jar），供前端选择启动路径。
     */
    public Map<String, Object> browse(String dir) {
        Map<String, Object> r = new HashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();

        if (dir == null || dir.trim().isEmpty()) {
            r.put("path", "");
            r.put("parent", null);
            for (File root : File.listRoots()) {
                Map<String, Object> m = new HashMap<String, Object>();
                m.put("name", root.getAbsolutePath());
                m.put("path", root.getAbsolutePath());
                m.put("dir", true);
                items.add(m);
            }
            r.put("items", items);
            return r;
        }

        File d = new File(dir.trim());
        if (!d.isDirectory()) {
            throw new BizException("目录不存在：" + dir);
        }
        r.put("path", d.getAbsolutePath());
        File parent = d.getParentFile();
        r.put("parent", parent == null ? null : parent.getAbsolutePath());

        File[] children = d.listFiles();
        if (children != null) {
            for (File f : children) {
                String n = f.getName();
                if (n.startsWith("$")) continue; // 跳过系统回收目录
                boolean isExe = false;
                String ln = n.toLowerCase();
                for (String ext : new String[]{".exe", ".lnk", ".bat", ".cmd", ".url", ".jar"}) {
                    if (ln.endsWith(ext)) {
                        isExe = true;
                        break;
                    }
                }
                if (f.isDirectory() || isExe) {
                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("name", n);
                    m.put("path", f.getAbsolutePath());
                    m.put("dir", f.isDirectory());
                    items.add(m);
                }
            }
        }
        items.sort((a, b) -> {
            boolean da = (Boolean) a.get("dir");
            boolean db = (Boolean) b.get("dir");
            if (da != db) return da ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });
        r.put("items", items);
        return r;
    }
}

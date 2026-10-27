package com.lifedesk.nativeapp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lifedesk.common.BizException;
import com.lifedesk.nativeapp.entity.NativeApp;
import com.lifedesk.nativeapp.mapper.NativeAppMapper;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义本地应用 Service。
 *
 * <p><b>浏览器硬限制说明</b>：网页无法像 iframe 一样嵌入原生应用窗口（浏览器安全模型决定）。
 * 本服务采用「真实启动 + 窗口画面流嵌入 + 一键置前」的近似方案：</p>
 * <ul>
 *   <li>launch：ProcessBuilder 在本机真实启动应用（原应用功能完全不变），记录进程 PID；</li>
 *   <li>screenshot：JNA PrintWindow 抓取应用主窗口客户区画面，前端定时轮询展示；</li>
 *   <li>focus：把原生窗口还原并置前，用户在真实窗口上交互；</li>
 *   <li>stop：结束进程。</li>
 * </ul>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
public class NativeAppService {

    private final NativeAppMapper nativeAppMapper;

    /** 运行中的进程：appId -> Process */
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();
    /** 运行中的进程：appId -> pid */
    private final Map<Long, Integer> pids = new ConcurrentHashMap<>();

    public NativeAppService(NativeAppMapper nativeAppMapper) {
        this.nativeAppMapper = nativeAppMapper;
    }

    @PreDestroy
    public void shutdown() {
        // 后端关闭时不杀用户应用（应用独立运行），仅清引用
        processes.clear();
        pids.clear();
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
        return existed;
    }

    public void delete(Long id) {
        requireApp(id);
        stop(id);
        nativeAppMapper.deleteById(id);
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

    // ===================== 启动 / 停止 =====================

    /**
     * 启动本地应用。
     * .lnk 快捷方式与需要 shell 语义的路径走 cmd start，普通 exe 直接 exec。
     */
    public Map<String, Object> launch(Long id) {
        NativeApp app = requireApp(id);

        // 已在运行则直接置前
        if (isRunning(id)) {
            focus(id);
            Map<String, Object> r = new HashMap<String, Object>();
            r.put("pid", pids.get(id));
            r.put("alreadyRunning", true);
            return r;
        }

        String path = app.getAppPath().trim();
        String lower = path.toLowerCase();
        File file = new File(path);

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
            Process p = pb.start();
            processes.put(id, p);
            Integer pid = resolvePid(p);
            if (pid != null) {
                pids.put(id, pid);
            }
            log.info("[NativeApp] 启动应用 {} 成功 pid={}", app.getName(), pid);
            // 等待窗口创建（多数应用启动到出窗口 <2s）
            if (pid != null) {
                waitForWindow(pid, 5000);
            }
            Map<String, Object> r = new HashMap<String, Object>();
            r.put("pid", pid);
            r.put("alreadyRunning", false);
            return r;
        } catch (Exception e) {
            log.error("[NativeApp] 启动失败 {}：{}", path, e.getMessage());
            throw new BizException("启动失败：" + e.getMessage());
        }
    }

    /** Java 8 下通过 ProcessImpl.handle 反射 + Kernel32.GetProcessId 拿子进程 PID */
    private Integer resolvePid(Process p) {
        try {
            Field f = p.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            long handle = f.getLong(p);
            return Kernel32.INSTANCE.GetProcessId(new WinNT.HANDLE(new Pointer(handle)));
        } catch (Exception e) {
            log.warn("[NativeApp] 获取 PID 失败：{}", e.getMessage());
            return null;
        }
    }

    public boolean isRunning(Long id) {
        Process p = processes.get(id);
        if (p != null && p.isAlive()) {
            return true;
        }
        // Java 进程对象已结束不代表窗口进程结束（cmd start 场景），再看 pid 是否还有顶层窗口
        Integer pid = pids.get(id);
        if (pid != null && findMainWindow(pid) != null) {
            return true;
        }
        if (p != null && !p.isAlive()) {
            processes.remove(id);
        }
        return false;
    }

    public void stop(Long id) {
        requireApp(id);
        Process p = processes.remove(id);
        Integer pid = pids.remove(id);
        if (pid == null && p != null) {
            pid = resolvePid(p);
        }
        if (pid != null) {
            // TerminateProcess
            WinNT.HANDLE h = Kernel32.INSTANCE.OpenProcess(Kernel32.PROCESS_TERMINATE, false, pid);
            if (h != null) {
                try {
                    Kernel32.INSTANCE.TerminateProcess(h, 0);
                } finally {
                    Kernel32.INSTANCE.CloseHandle(h);
                }
            }
            log.info("[NativeApp] 已结束应用 pid={}", pid);
        }
    }

    // ===================== 窗口：查找 / 置前 / 截图 =====================

    /** 轮询等待指定 PID 出现可见主窗口（应用启动有延迟） */
    private void waitForWindow(int pid, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (findMainWindow(pid) != null) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 找 PID 对应的可见顶层窗口（含窗口标题的优先） */
    private WinDef.HWND findMainWindow(final int pid) {
        final List<WinDef.HWND> found = new ArrayList<WinDef.HWND>();
        final List<WinDef.HWND> titled = new ArrayList<WinDef.HWND>();
        User32.INSTANCE.EnumWindows(new User32.WNDENUMPROC() {
            @Override
            public boolean callback(WinDef.HWND hwnd, Pointer data) {
                if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                    return true;
                }
                com.sun.jna.ptr.IntByReference pidRef = new com.sun.jna.ptr.IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
                if (pidRef.getValue() != pid) {
                    return true;
                }
                // 排除无尺寸的隐藏辅助窗口
                WinDef.RECT rc = new WinDef.RECT();
                if (!User32.INSTANCE.GetWindowRect(hwnd, rc)) {
                    return true;
                }
                if (rc.right - rc.left <= 0 || rc.bottom - rc.top <= 0) {
                    return true;
                }
                found.add(hwnd);
                char[] buf = new char[256];
                int len = User32.INSTANCE.GetWindowText(hwnd, buf, 256);
                if (len > 0) {
                    titled.add(hwnd);
                }
                return true;
            }
        }, null);
        if (!titled.isEmpty()) {
            return titled.get(0);
        }
        return found.isEmpty() ? null : found.get(0);
    }

    /** 把应用窗口还原并置前 */
    public void focus(Long id) {
        Integer pid = pids.get(id);
        WinDef.HWND hwnd = pid == null ? null : findMainWindow(pid);
        if (hwnd == null) {
            throw new BizException("未找到应用窗口（应用可能未启动）");
        }
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
        User32.INSTANCE.SetForegroundWindow(hwnd);
    }

    /** 状态查询：appId -> {running, pid, hasWindow} */
    public Map<Long, Map<String, Object>> status() {
        Map<Long, Map<String, Object>> r = new HashMap<Long, Map<String, Object>>();
        Set<Long> ids = new HashSet<Long>(processes.keySet());
        ids.addAll(pids.keySet());
        for (Long id : ids) {
            Map<String, Object> m = new HashMap<String, Object>();
            boolean running = isRunning(id);
            m.put("running", running);
            m.put("pid", pids.get(id));
            Integer pid = pids.get(id);
            m.put("hasWindow", running && pid != null && findMainWindow(pid) != null);
            r.put(id, m);
        }
        return r;
    }

    /**
     * 抓取应用主窗口客户区画面（PNG 字节）。
     * 用 PrintWindow(PW_CLIENTONLY)，应用窗口可以不在前台也能抓到。
     */
    public byte[] screenshot(Long id) {
        NativeApp app = requireApp(id);
        if (!isRunning(id)) {
            throw new BizException("APP_NOT_RUNNING");
        }
        Integer pid = pids.get(id);
        WinDef.HWND hwnd = pid == null ? null : findMainWindow(pid);
        if (hwnd == null) {
            throw new BizException("APP_NO_WINDOW");
        }

        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetClientRect(hwnd, rect);
        int w = rect.right - rect.left;
        int h = rect.bottom - rect.top;
        if (w <= 0 || h <= 0) {
            throw new BizException("APP_NO_WINDOW");
        }

        WinDef.HDC hdcWindow = User32.INSTANCE.GetDC(hwnd);
        WinDef.HDC hdcMem = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);
        WinDef.HBITMAP hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, w, h);
        WinNT.HANDLE oldObj = GDI32.INSTANCE.SelectObject(hdcMem, hBitmap);
        try {
            // PW_CLIENTONLY = 1：只抓客户区
            User32.INSTANCE.PrintWindow(hwnd, hdcMem, 1);

            WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO();
            bmi.bmiHeader.biWidth = w;
            bmi.bmiHeader.biHeight = -h; // top-down
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

            Memory buffer = new Memory((long) w * h * 4);
            GDI32.INSTANCE.GetDIBits(hdcWindow, hBitmap, 0, h, buffer, bmi, WinGDI.DIB_RGB_COLORS);

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            int[] pixels = new int[w * h];
            for (int i = 0; i < pixels.length; i++) {
                int bgr = buffer.getInt((long) i * 4);
                int b = (bgr >> 16) & 0xFF;
                int g = (bgr >> 8) & 0xFF;
                int r = bgr & 0xFF;
                pixels[i] = (r << 16) | (g << 8) | b;
            }
            img.setRGB(0, 0, w, h, pixels, 0, w);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new BizException("抓取窗口画面失败：" + e.getMessage());
        } finally {
            GDI32.INSTANCE.SelectObject(hdcMem, oldObj);
            GDI32.INSTANCE.DeleteObject(hBitmap);
            GDI32.INSTANCE.DeleteDC(hdcMem);
            User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);
        }
    }

    // ===================== 目录浏览（前端选择本地应用路径用） =====================

    /**
     * 浏览本地目录：dir 为空时返回盘符列表。
     * 只返回目录 + 可执行文件（exe/lnk/bat/cmd/url），供前端选择启动路径。
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

    /** JNA Native 引用（防止 unused import 警告占位） */
    @SuppressWarnings("unused")
    private static final Object NATIVE_KEEPALIVE = Native.VERSION;
}

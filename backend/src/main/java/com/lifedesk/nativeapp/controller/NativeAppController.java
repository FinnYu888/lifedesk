package com.lifedesk.nativeapp.controller;

import com.lifedesk.common.R;
import com.lifedesk.nativeapp.entity.NativeApp;
import com.lifedesk.nativeapp.service.NativeAppService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 自定义本地应用 Controller。
 *
 * <p>本地应用点击后由后端真实启动，窗口画面以截图流形式嵌入系统展示，
 * 交互在原生窗口进行（一键置前）。接口前缀 /api/native-app。</p>
 *
 * @author LifeDesk
 */
@RestController
@RequestMapping("/native-app")
public class NativeAppController {

    private final NativeAppService nativeAppService;

    public NativeAppController(NativeAppService nativeAppService) {
        this.nativeAppService = nativeAppService;
    }

    /** 应用列表 */
    @GetMapping
    public R<List<NativeApp>> list() {
        return R.ok(nativeAppService.list());
    }

    /** 新增应用 */
    @PostMapping
    public R<NativeApp> create(@RequestBody NativeApp app) {
        return R.ok(nativeAppService.create(app));
    }

    /** 更新应用 */
    @PutMapping("/{id}")
    public R<NativeApp> update(@PathVariable Long id, @RequestBody NativeApp app) {
        return R.ok(nativeAppService.update(id, app));
    }

    /** 删除应用 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        nativeAppService.delete(id);
        return R.ok(null);
    }

    /** 启动应用（已运行则置前） */
    @PostMapping("/{id}/launch")
    public R<Map<String, Object>> launch(@PathVariable Long id) {
        return R.ok(nativeAppService.launch(id));
    }

    /** 结束应用进程 */
    @PostMapping("/{id}/stop")
    public R<Void> stop(@PathVariable Long id) {
        nativeAppService.stop(id);
        return R.ok(null);
    }

    /** 应用窗口置前 */
    @PostMapping("/{id}/focus")
    public R<Void> focus(@PathVariable Long id) {
        nativeAppService.focus(id);
        return R.ok(null);
    }

    /** 全部应用运行状态 */
    @GetMapping("/status")
    public R<Map<Long, Map<String, Object>>> status() {
        return R.ok(nativeAppService.status());
    }

    /** 应用窗口实时画面（PNG；前端定时轮询） */
    @GetMapping(value = "/{id}/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> screenshot(@PathVariable Long id) {
        byte[] png = nativeAppService.screenshot(id);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    /** 浏览本地目录（选择启动路径用） */
    @GetMapping("/browse")
    public R<Map<String, Object>> browse(@RequestParam(required = false) String dir) {
        return R.ok(nativeAppService.browse(dir));
    }
}

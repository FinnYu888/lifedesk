package com.lifedesk.nativeapp.controller;

import com.lifedesk.common.R;
import com.lifedesk.nativeapp.entity.NativeApp;
import com.lifedesk.nativeapp.service.NativeAppService;
import org.springframework.http.CacheControl;
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
 * 常用App（自定义本地应用）Controller。
 *
 * <p>点击应用由后端在本机真实启动（不嵌入窗口），展示图标与桌面快捷方式一致。
 * 接口前缀 /api/native-app。</p>
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

    /** 启动应用（本机真实启动） */
    @PostMapping("/{id}/launch")
    public R<Map<String, Object>> launch(@PathVariable Long id) {
        return R.ok(nativeAppService.launch(id));
    }

    /** 应用图标 PNG（与桌面快捷方式一致；失败时前端回退占位图标） */
    @GetMapping(value = "/{id}/icon", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> icon(@PathVariable Long id) {
        byte[] png = nativeAppService.icon(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    /** 浏览本地目录（选择启动路径用） */
    @GetMapping("/browse")
    public R<Map<String, Object>> browse(@RequestParam(required = false) String dir) {
        return R.ok(nativeAppService.browse(dir));
    }
}

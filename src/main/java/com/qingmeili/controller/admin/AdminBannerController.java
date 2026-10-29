package com.qingmeili.controller.admin;

import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Banner;
import com.qingmeili.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台管理 - 轮播图管理
 */
@RestController
@RequestMapping("/api/admin/banners")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    public Result<List<Banner>> list() {
        return bannerService.listAll();
    }

    @PostMapping
    @OperationLog(module = "轮播图管理", action = "新增")
    public Result<Banner> create(@RequestBody Banner banner) {
        return bannerService.create(banner);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "轮播图管理", action = "修改")
    public Result<Banner> update(@PathVariable Long id, @RequestBody Banner banner) {
        return bannerService.update(id, banner);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "轮播图管理", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        return bannerService.delete(id);
    }
}

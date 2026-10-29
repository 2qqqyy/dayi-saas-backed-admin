package com.qingmeili.controller.admin;

import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Scenic;
import com.qingmeili.service.ScenicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台管理 - 景点管理
 */
@RestController
@RequestMapping("/api/admin/scenic")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminScenicController {

    private final ScenicService scenicService;

    @GetMapping
    public Result<PageResult<Scenic>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return scenicService.page(page, pageSize, keyword, category);
    }

    @PostMapping
    @OperationLog(module = "景点管理", action = "新增")
    public Result<Scenic> create(@RequestBody Scenic scenic) {
        return scenicService.create(scenic);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "景点管理", action = "修改")
    public Result<Scenic> update(@PathVariable Long id, @RequestBody Scenic scenic) {
        return scenicService.update(id, scenic);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "景点管理", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        return scenicService.delete(id);
    }
}

package com.qingmeili.controller.admin;

import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.village.VillageDTO;
import com.qingmeili.entity.Village;
import com.qingmeili.service.VillageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台管理 - 乡村管理
 */
@RestController
@RequestMapping("/api/admin/villages")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminVillageController {

    private final VillageService villageService;

    @GetMapping
    public Result<PageResult<Village>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return villageService.page(page, pageSize, keyword);
    }

    @GetMapping("/{id}")
    public Result<Village> detail(@PathVariable Long id) {
        return villageService.getDetail(id);
    }

    @PostMapping
    @OperationLog(module = "乡村管理", action = "新增")
    public Result<Village> create(@Valid @RequestBody VillageDTO dto) {
        return villageService.create(dto);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "乡村管理", action = "修改")
    public Result<Village> update(@PathVariable Long id, @Valid @RequestBody VillageDTO dto) {
        return villageService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "乡村管理", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        return villageService.delete(id);
    }
}

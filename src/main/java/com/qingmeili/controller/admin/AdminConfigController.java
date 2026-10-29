package com.qingmeili.controller.admin;

import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.SysConfig;
import com.qingmeili.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台管理 - 系统配置
 */
@RestController
@RequestMapping("/api/admin/configs")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminConfigController {

    private final SysConfigService sysConfigService;

    @GetMapping
    public Result<List<SysConfig>> list() {
        return sysConfigService.listAll();
    }

    @PutMapping
    @OperationLog(module = "系统配置", action = "批量更新")
    public Result<Void> batchUpdate(@RequestBody List<Map<String, String>> configs) {
        return sysConfigService.batchUpdate(configs);
    }
}

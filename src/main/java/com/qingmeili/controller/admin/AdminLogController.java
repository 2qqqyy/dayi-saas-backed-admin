package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.OperationLogEntity;
import com.qingmeili.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台管理 - 操作日志
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminLogController {

    private final OperationLogMapper operationLogMapper;

    /** 列表：支持操作人模糊、模块与结果精确筛选，筛选下沉到 SQL，避免前端只过滤当前页 */
    @GetMapping
    public Result<PageResult<OperationLogEntity>> list(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<OperationLogEntity> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator)) {
            wrapper.like(OperationLogEntity::getAdminName, operator);
        }
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLogEntity::getModule, module);
        }
        if (StringUtils.hasText(result)) {
            wrapper.eq(OperationLogEntity::getResult, result);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OperationLogEntity::getAdminName, keyword)
                    .or().like(OperationLogEntity::getAction, keyword)
                    .or().like(OperationLogEntity::getDetail, keyword));
        }
        wrapper.orderByDesc(OperationLogEntity::getCreateTime);
        Page<OperationLogEntity> pageResult = operationLogMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, pageSize));
    }

    /** 模块清单：用于筛选下拉，避免前端从当前页数据里猜可选值 */
    @GetMapping("/modules")
    public Result<List<String>> modules() {
        List<String> modules = operationLogMapper.selectList(
                        new LambdaQueryWrapper<OperationLogEntity>()
                                .select(OperationLogEntity::getModule)
                                .isNotNull(OperationLogEntity::getModule)
                                .groupBy(OperationLogEntity::getModule))
                .stream().map(OperationLogEntity::getModule).filter(StringUtils::hasText).toList();
        return Result.ok(modules);
    }
}

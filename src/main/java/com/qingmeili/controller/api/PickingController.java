package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Orchard;
import com.qingmeili.entity.PickingSession;
import com.qingmeili.mapper.PickingSessionMapper;
import com.qingmeili.service.OrchardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C端 - 采摘（公开接口）
 */
@RestController
@RequestMapping("/api/picking")
@RequiredArgsConstructor
public class PickingController {

    private final OrchardService orchardService;
    private final PickingSessionMapper pickingSessionMapper;

    @GetMapping("/orchards")
    public Result<PageResult<Orchard>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return orchardService.page(page, pageSize, keyword);
    }

    @GetMapping("/orchards/{id}")
    public Result<java.util.Map<String, Object>> detail(@PathVariable Long id) {
        return orchardService.getDetail(id);
    }

    /** H5 别名：GET /api/picking（列表附带可预约场次） */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> listAlias(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        PageResult<Orchard> result = orchardService.page(page, pageSize, keyword).getData();
        List<Map<String, Object>> list = result.getList().stream().map(orchard -> {
            List<PickingSession> sessions = pickingSessionMapper.selectList(
                    new LambdaQueryWrapper<PickingSession>()
                            .eq(PickingSession::getOrchardId, orchard.getId())
                            .eq(PickingSession::getStatus, 1)
                            .ge(PickingSession::getSlotDate, LocalDate.now())
                            .orderByAsc(PickingSession::getSlotDate));
            Map<String, Object> item = new HashMap<>();
            item.put("orchard", orchard);
            item.put("sessions", sessions);
            return item;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** H5 别名：GET /api/picking/{id} */
    @GetMapping("/{id}")
    public Result<java.util.Map<String, Object>> detailAlias(@PathVariable Long id) {
        return orchardService.getDetail(id);
    }
}

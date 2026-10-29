package com.qingmeili.controller.merchant;

import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.picking.PickingSessionDTO;
import com.qingmeili.entity.PickingSession;
import com.qingmeili.service.PickingSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 商户端 - 采摘档期管理
 */
@RestController
@RequestMapping("/api/merchant/picking/sessions")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantPickingController {

    private final PickingSessionService pickingSessionService;

    @GetMapping
    public Result<List<PickingSession>> list(@RequestParam(required = false) Integer status) {
        return pickingSessionService.listByMerchant(UserContext.getMerchantId(), status);
    }

    @PostMapping
    @OperationLog(module = "采摘档期", action = "创建档期")
    public Result<PickingSession> create(@Valid @RequestBody PickingSessionDTO dto) {
        return pickingSessionService.create(dto);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "采摘档期", action = "编辑档期")
    public Result<PickingSession> update(@PathVariable Long id, @Valid @RequestBody PickingSessionDTO dto) {
        requireOwned(id);
        return pickingSessionService.update(id, dto);
    }

    @PutMapping("/{id}/status")
    @OperationLog(module = "采摘档期", action = "更新档期状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        requireOwned(id);
        return pickingSessionService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "采摘档期", action = "删除档期")
    public Result<Void> delete(@PathVariable Long id) {
        requireOwned(id);
        return pickingSessionService.delete(id);
    }

    /** 列表按 merchantId 过滤，但按 id 的写操作会绕过它，必须逐个校验归属 */
    private void requireOwned(Long id) {
        PickingSession session = pickingSessionService.getById(id);
        if (session == null || !Objects.equals(session.getMerchantId(), UserContext.getMerchantId())) {
            throw new BusinessException("档期不存在");
        }
    }
}

package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.PickingSession;
import com.qingmeili.entity.Product;
import com.qingmeili.entity.HomestayRoom;
import com.qingmeili.mapper.PickingSessionMapper;
import com.qingmeili.mapper.ProductMapper;
import com.qingmeili.mapper.HomestayRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台管理 - 审核管理
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminAuditController {

    private final ProductMapper productMapper;
    private final PickingSessionMapper pickingSessionMapper;
    private final HomestayRoomMapper homestayRoomMapper;

    @GetMapping("/products")
    public Result<PageResult<Product>> pendingProducts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Product> pageParam = new Page<>(page, pageSize);
        Page<Product> result = productMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Product>().eq(Product::getAuditStatus, 0)
                        .orderByDesc(Product::getCreateTime));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @GetMapping("/sessions")
    public Result<PageResult<PickingSession>> pendingSessions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<PickingSession> pageParam = new Page<>(page, pageSize);
        Page<PickingSession> result = pickingSessionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<PickingSession>().eq(PickingSession::getAuditStatus, 0)
                        .orderByDesc(PickingSession::getCreateTime));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @GetMapping("/rooms")
    public Result<PageResult<HomestayRoom>> pendingRooms(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<HomestayRoom> pageParam = new Page<>(page, pageSize);
        Page<HomestayRoom> result = homestayRoomMapper.selectPage(pageParam,
                new LambdaQueryWrapper<HomestayRoom>()
                        .eq(HomestayRoom::getAuditStatus, 0)
                        .orderByDesc(HomestayRoom::getCreateTime));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @PostMapping("/{type}/{id}")
    @OperationLog(module = "审核管理", action = "审核")
    public Result<Void> audit(@PathVariable String type, @PathVariable Long id,
                              @RequestParam String action, @RequestParam(required = false) String reason) {
        switch (type) {
            case "product" -> {
                Product product = productMapper.selectById(id);
                if (product == null) throw new BusinessException("商品不存在");
                if ("approve".equals(action)) {
                    product.setAuditStatus(1);
                    product.setAuditRemark(null);
                    product.setOnShelf(1);
                } else {
                    product.setAuditStatus(2);
                    product.setAuditRemark(reason);
                }
                productMapper.updateById(product);
            }
            case "session" -> {
                PickingSession session = pickingSessionMapper.selectById(id);
                if (session == null) throw new BusinessException("档期不存在");
                session.setAuditStatus("approve".equals(action) ? 1 : 2);
                session.setAuditRemark("approve".equals(action) ? null : reason);
                pickingSessionMapper.updateById(session);
            }
            case "room" -> {
                HomestayRoom room = homestayRoomMapper.selectById(id);
                if (room == null) throw new BusinessException("房型不存在");
                if ("approve".equals(action)) {
                    room.setAuditStatus(1);
                    room.setAuditRemark(null);
                    room.setStatus(1);
                } else {
                    room.setAuditStatus(2);
                    room.setAuditRemark(reason);
                }
                homestayRoomMapper.updateById(room);
            }
            default -> throw new BusinessException("无效的审核类型");
        }
        return Result.ok("审核完成", null);
    }
}

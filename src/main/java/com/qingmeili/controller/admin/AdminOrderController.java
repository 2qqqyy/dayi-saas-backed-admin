package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.OrderItem;
import com.qingmeili.entity.OrderProduct;
import com.qingmeili.mapper.OrderItemMapper;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.OrderPickingMapper;
import com.qingmeili.mapper.OrderHomestayMapper;
import com.qingmeili.mapper.OrderProductMapper;
import com.qingmeili.mapper.UserMapper;
import com.qingmeili.entity.OrderPicking;
import com.qingmeili.entity.OrderHomestay;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 后台管理 - 订单管理
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderMapper orderMapper;
    private final com.qingmeili.service.OrderService orderService;
    private final com.qingmeili.service.UserMessageService userMessageService;
    private final OrderPickingMapper orderPickingMapper;
    private final OrderHomestayMapper orderHomestayMapper;
    private final OrderProductMapper orderProductMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Order> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (type != null) wrapper.eq(Order::getType, type);
        if (StringUtils.hasText(status)) wrapper.eq(Order::getStatus, status);
        if (merchantId != null) wrapper.eq(Order::getMerchantId, merchantId);
        if (StringUtils.hasText(keyword)) wrapper.like(Order::getOrderNo, keyword);
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> result = orderMapper.selectPage(pageParam, wrapper);

        java.util.List<Map<String, Object>> list = result.getRecords().stream().map(this::toListMap).toList();

        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** 订单 + 用户昵称/商户名，供管理端列表使用 */
    private Map<String, Object> toListMap(Order order) {
        Map<String, Object> map = new HashMap<>();
        map.put("order", order);
        String userName = "游客";
        if (order.getUserId() != null) {
            com.qingmeili.entity.User u = userMapper.selectById(order.getUserId());
            if (u != null && u.getNickname() != null) userName = u.getNickname();
        }
        map.put("userName", userName);
        return map;
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        Map<String, Object> data = toListMap(order);
        switch (order.getType()) {
            case 1 -> {
                OrderPicking picking = orderPickingMapper.selectOne(
                        new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, id));
                data.put("picking", picking);
            }
            case 2 -> {
                OrderHomestay homestay = orderHomestayMapper.selectOne(
                        new LambdaQueryWrapper<OrderHomestay>().eq(OrderHomestay::getOrderId, id));
                data.put("homestay", homestay);
            }
            case 3 -> {
                OrderProduct product = orderProductMapper.selectOne(
                        new LambdaQueryWrapper<OrderProduct>().eq(OrderProduct::getOrderId, id));
                data.put("product", product);
                var items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
                data.put("items", items);
            }
        }
        return Result.ok(data);
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{id}/refund")
    public Result<Void> refund(@PathVariable Long id, @RequestParam BigDecimal refundAmount, @RequestParam(required = false) String reason) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 仅已支付且未完成/未取消的订单可退款
        if (!java.util.Set.of("pending_use", "pending_delivery", "shipped", "refunding").contains(order.getStatus())) {
            throw new BusinessException("当前订单状态不支持退款");
        }
        BigDecimal paid = order.getPayAmount() != null ? order.getPayAmount() : order.getAmount();
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款金额必须大于 0");
        }
        if (paid != null && refundAmount.compareTo(paid) > 0) {
            throw new BusinessException("退款金额不能超过实付金额");
        }
        orderService.restoreStock(order);
        orderService.reverseSettlement(order);
        order.setStatus("refunded");
        order.setRefundAmount(refundAmount);
        order.setRefundTime(LocalDateTime.now());
        order.setCancelReason(reason != null ? reason : "");
        orderMapper.updateById(order);
        userMessageService.notifyOrderEvent(order, "refunded");
        return Result.ok("退款成功", null);
    }

    /** 发货：商品订单待发货 → 配送中 */
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id,
                             @RequestParam(required = false) String company,
                             @RequestParam(required = false) String trackingNo) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!"pending_delivery".equals(order.getStatus())) {
            throw new BusinessException("当前状态不支持发货");
        }
        order.setStatus("shipped");
        String remark = order.getRemark() != null ? order.getRemark() : "";
        order.setRemark((remark + " 物流:" + (company != null ? company : "") + " " + (trackingNo != null ? trackingNo : "")).trim());
        orderMapper.updateById(order);
        userMessageService.notifyOrderEvent(order, "shipped");
        return Result.ok("发货成功", null);
    }

    /** 退款单列表：退款中 / 已退款 / 退款被拒 */
    @GetMapping("/refunds")
    public Result<PageResult<Map<String, Object>>> refunds(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Order> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(Order::getStatus, status);
        } else {
            wrapper.in(Order::getStatus, "refunding", "refunded", "refund_rejected");
        }
        wrapper.orderByDesc(Order::getUpdateTime);
        Page<Order> result = orderMapper.selectPage(pageParam, wrapper);
        java.util.List<Map<String, Object>> list = result.getRecords().stream().map(this::toListMap).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** 同意退款 */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/refunds/{id}/approve")
    public Result<Void> approveRefund(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!"refunding".equals(order.getStatus())) {
            throw new BusinessException("该订单不在退款流程中");
        }
        orderService.restoreStock(order);
        orderService.reverseSettlement(order);
        order.setStatus("refunded");
        order.setRefundAmount(order.getPayAmount() != null ? order.getPayAmount() : order.getAmount());
        order.setRefundTime(LocalDateTime.now());
        orderMapper.updateById(order);
        userMessageService.notifyOrderEvent(order, "refunded");
        return Result.ok("已同意退款", null);
    }

    /** 拒绝退款 */
    @PostMapping("/refunds/{id}/reject")
    public Result<Void> rejectRefund(@PathVariable Long id, @RequestParam(required = false) String reason) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!"refunding".equals(order.getStatus())) {
            throw new BusinessException("该订单不在退款流程中");
        }
        order.setStatus("refund_rejected");
        order.setCancelReason(reason != null ? reason : "");
        orderMapper.updateById(order);
        userMessageService.notifyOrderEvent(order, "refund_rejected");
        return Result.ok("已拒绝退款", null);
    }
}

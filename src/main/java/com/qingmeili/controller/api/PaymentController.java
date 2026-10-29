package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Order;
import com.qingmeili.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * C端 - 支付（模拟支付通道，后续接入微信支付时替换 markPaid 内部逻辑）
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderMapper orderMapper;

    @PostMapping("/pay")
    public Result<Map<String, Object>> pay(@RequestBody Map<String, Object> body) {
        String orderId = String.valueOf(body.getOrDefault("orderId", ""));
        String method = String.valueOf(body.getOrDefault("method", "wechat"));

        Order order = findOrder(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!"pending_payment".equals(order.getStatus())) {
            throw new BusinessException("订单状态不支持支付");
        }

        // 采摘/民宿支付后待使用，商品支付后待发货
        String next = switch (order.getType() != null ? order.getType() : 3) {
            case 1, 2 -> "pending_use";
            default -> "pending_delivery";
        };
        order.setStatus(next);
        order.setPayAmount(order.getAmount());
        order.setPayTime(LocalDateTime.now());
        order.setPayMethod(method);
        order.setTransactionId("MOCK" + System.currentTimeMillis());
        orderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orderId", orderId);
        result.put("paidAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return Result.ok(result);
    }

    private Order findOrder(String orderId) {
        // 优先按订单号匹配，其次按主键
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderId)
                .eq(Order::getUserId, UserContext.getUserId())
                .last("limit 1"));
        if (order == null && orderId.matches("\\d+")) {
            order = orderMapper.selectById(Long.valueOf(orderId));
        }
        return order;
    }
}

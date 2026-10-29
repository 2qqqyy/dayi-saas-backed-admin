package com.qingmeili.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.entity.Order;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.service.OrderService;
import com.qingmeili.service.SysConfigService;
import com.qingmeili.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时关单：下单后 order_expire_minutes（默认 30）分钟未支付自动取消并回补库存，
 * 否则放弃支付的订单会永久停在 pending_payment 占用名额。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final SysConfigService sysConfigService;
    private final UserMessageService userMessageService;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void closeExpiredOrders() {
        int minutes = 30;
        try {
            String v = sysConfigService.getValue("order_expire_minutes").getData();
            if (v != null && !v.isBlank()) {
                minutes = Integer.parseInt(v.trim());
            }
        } catch (NumberFormatException ignored) {
            /* 配置坏了就用默认值，关单不能停 */
        }
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(minutes);
        List<Order> expired = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, "pending_payment")
                        .lt(Order::getCreateTime, deadline));
        for (Order order : expired) {
            // 条件更新：期间用户已支付的订单不会被误取消
            int updated = orderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, "pending_payment")
                    .set(Order::getStatus, "cancelled")
                    .set(Order::getCancelReason, "支付超时自动取消")
                    .set(Order::getCancelTime, LocalDateTime.now()));
            if (updated == 0) {
                continue;
            }
            try {
                orderService.restoreStock(order);
                userMessageService.notifyOrderEvent(order, "cancelled");
            } catch (Exception e) {
                log.warn("超时关单回补失败 orderId=" + order.getId() + ": " + e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("支付超时关单 {} 笔", expired.size());
        }
    }
}

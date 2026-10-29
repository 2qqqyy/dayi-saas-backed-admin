package com.qingmeili.controller.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.OrderPicking;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.OrderPickingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 商户端 - 消息中心
 *
 * 说明：后端没有独立的站内信表，这里基于商户的真实订单事件
 * 动态生成消息（待核销 / 待发货 / 退款中 / 已完成），
 * 保证消息页展示的是真实业务数据而非硬编码 mock。
 * 已读状态为前端本地维护（见 message api 的 read 标记）。
 */
@RestController
@RequestMapping("/api/merchant/messages")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantMessageController {

    private final OrderMapper orderMapper;
    private final OrderPickingMapper orderPickingMapper;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        Long merchantId = UserContext.getMerchantId();
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId)
                .orderByDesc(Order::getCreateTime)
                .last("LIMIT 50"));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> m = buildMessage(o, merchantId);
            if (m != null) {
                list.add(m);
            }
        }
        // 按时间倒序
        list.sort((a, b) -> String.valueOf(b.get("time")).compareTo(String.valueOf(a.get("time"))));
        return Result.ok(list);
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        Long merchantId = UserContext.getMerchantId();
        long n = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId)
                .in(Order::getStatus, List.of("pending_use", "pending_delivery", "refunding")));
        return Result.ok((int) n);
    }

    private Map<String, Object> buildMessage(Order o, Long merchantId) {
        String st = o.getStatus() == null ? "" : o.getStatus();
        String type;
        String title;
        String content;
        String titleEn;
        String contentEn;
        String goods = o.getTitle() == null ? "" : o.getTitle();
        switch (st) {
            case "pending_use" -> {
                type = "ORDER";
                title = "有新的采摘订单待核销";
                content = o.getTitle() + " 已支付，请准备接待并核销。";
                titleEn = "New picking order to verify";
                contentEn = goods + " is paid. Prepare the visit and verify the code.";
            }
            case "pending_delivery" -> {
                type = "ORDER";
                title = "有订单待发货";
                content = o.getTitle() + " 已支付，请尽快安排发货。";
                titleEn = "Order awaiting shipment";
                contentEn = goods + " is paid. Please arrange delivery soon.";
            }
            case "refunding" -> {
                type = "ORDER";
                title = "有退款申请待处理";
                content = o.getTitle() + " 用户申请退款，请及时处理。";
                titleEn = "Refund request to handle";
                contentEn = "The buyer requested a refund for " + goods + ". Please handle it in time.";
            }
            case "completed" -> {
                type = "SYSTEM";
                title = "订单已完成";
                content = o.getTitle() + " 已完成，款项将进入结算。";
                titleEn = "Order completed";
                contentEn = goods + " is completed. The payment will enter settlement.";
            }
            case "shipping" -> {
                type = "ORDER";
                title = "订单已发货";
                content = o.getTitle() + " 已发货，等待买家收货。";
                titleEn = "Order shipped";
                contentEn = goods + " has shipped and is waiting for the buyer.";
            }
            default -> {
                return null;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "msg-" + o.getId());
        m.put("merchantId", String.valueOf(merchantId));
        m.put("type", type);
        m.put("title", title);
        m.put("content", content);
        m.put("titleEn", titleEn);
        m.put("contentEn", contentEn);
        m.put("time", o.getCreateTime() != null ? o.getCreateTime().format(DF) : "");
        m.put("read", false);
        m.put("orderId", String.valueOf(o.getId()));
        return m;
    }
}

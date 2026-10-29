package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.MerchantPenalty;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.SupportTicket;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.MerchantPenaltyMapper;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.SupportTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台 - 异常预警。工作台顶部提示需要人工处理的积压项。
 */
@RestController
@RequestMapping("/api/admin/alerts")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminAlertController {

    private final OrderMapper orderMapper;
    private final SupportTicketMapper ticketMapper;
    private final MerchantMapper merchantMapper;
    private final MerchantPenaltyMapper penaltyMapper;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> alerts = new ArrayList<>();

        Long refunding = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "refunding"));
        add(alerts, "danger", "待处理退款", refunding, "/order/refund",
                "用户已提交退款申请，超过 24 小时未处理会持续积压");

        Long tickets = ticketMapper.selectCount(new LambdaQueryWrapper<SupportTicket>()
                .in(SupportTicket::getStatus, 0, 1));
        add(alerts, "warning", "待处理售后工单", tickets, "/support/ticket",
                "含商家未结案与已升级平台的工单");

        Long lateShip = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "pending_delivery")
                .lt(Order::getCreateTime, LocalDateTime.now().minusHours(24)));
        add(alerts, "warning", "超 24 小时未发货", lateShip, "/order/list",
                "商品订单支付后长期未发货，建议联系商家");

        Long suspended = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, 0));
        add(alerts, "info", "暂停营业 / 禁用商家", suspended, "/merchant/penalty",
                "含平台处罚与手动禁用，到期处罚会自动恢复");

        Long activePenalties = penaltyMapper.selectCount(new LambdaQueryWrapper<MerchantPenalty>()
                .eq(MerchantPenalty::getStatus, 1));
        add(alerts, "info", "生效中处罚", activePenalties, "/merchant/penalty", "");

        return Result.ok(alerts);
    }

    private void add(List<Map<String, Object>> alerts, String level, String title,
                     Long count, String path, String desc) {
        if (count == null || count == 0) {
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("title", title);
        m.put("count", count);
        m.put("path", path);
        m.put("desc", desc);
        alerts.add(m);
    }
}

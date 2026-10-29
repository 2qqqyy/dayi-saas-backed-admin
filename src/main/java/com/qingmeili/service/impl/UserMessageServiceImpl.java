package com.qingmeili.service.impl;

import com.qingmeili.entity.Order;
import com.qingmeili.entity.UserMessage;
import com.qingmeili.entity.User;
import com.qingmeili.mapper.UserMapper;
import com.qingmeili.mapper.UserMessageMapper;
import com.qingmeili.service.UserMessageService;
import com.qingmeili.service.WxMiniService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 订单事件 → 游客站内信。文案中英双语，与商家端消息中心口径一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMessageServiceImpl implements UserMessageService {

    private final UserMessageMapper userMessageMapper;
    private final UserMapper userMapper;
    private final WxMiniService wxMiniService;

    @Override
    public void notifyOrderEvent(Order order, String event) {
        if (order == null || order.getUserId() == null) {
            return;
        }
        String goods = order.getTitle() == null ? "" : order.getTitle();
        String type;
        String title;
        String titleEn;
        String content;
        String contentEn;
        switch (event) {
            case "shipped" -> {
                type = "ORDER";
                title = "订单已发货";
                titleEn = "Your order has shipped";
                content = goods + " 已发货，请留意收货。";
                contentEn = goods + " has shipped. Please watch for the delivery.";
            }
            case "refunded" -> {
                type = "REFUND";
                title = "退款已同意";
                titleEn = "Refund approved";
                content = goods + " 的退款申请已通过，款项将原路退回。";
                contentEn = "Your refund for " + goods + " is approved and will be returned via the original payment method.";
            }
            case "refund_rejected" -> {
                type = "REFUND";
                title = "退款被拒绝";
                titleEn = "Refund declined";
                content = goods + " 的退款申请被商家拒绝，详情见订单页说明。";
                contentEn = "The merchant declined your refund for " + goods + ". See the order page for details.";
            }
            case "verified" -> {
                type = "VERIFY";
                title = "核销成功";
                titleEn = "Checked in";
                content = goods + " 已核销，祝你玩得开心！";
                contentEn = goods + " is checked in. Enjoy your visit!";
            }
            case "rejected_by_merchant" -> {
                type = "REFUND";
                title = "预约被商家取消";
                titleEn = "Reservation cancelled by the merchant";
                content = goods + " 商家无法接待，已全额退款，名额已释放。";
                contentEn = "The merchant cannot host " + goods + ". A full refund has been issued and the quota released.";
            }
            case "cancelled" -> {
                type = "ORDER";
                title = "订单已取消";
                titleEn = "Order cancelled";
                content = goods + " 已取消，占用的名额与库存已释放。";
                contentEn = goods + " is cancelled. The quota and stock have been released.";
            }
            default -> {
                return;
            }
        }
        try {
            UserMessage m = new UserMessage();
            m.setUserId(order.getUserId());
            m.setType(type);
            m.setOrderId(order.getId());
            m.setTitle(title);
            m.setTitleEn(titleEn);
            m.setContent(content);
            m.setContentEn(contentEn);
            m.setReadFlag(0);
            m.setDeleted(0);
            m.setCreateTime(LocalDateTime.now());
            userMessageMapper.insert(m);
            pushWx(order, event, title, content);
        } catch (Exception e) {
            log.warn("写入游客站内信失败 orderId={} event={}: {}", order.getId(), event, e.getMessage());
        }
    }

    /** 站内信落地后再尝试订阅消息；用户未授权/未配置模板都会静默失败 */
    private void pushWx(Order order, String event, String title, String content) {
        try {
            User user = userMapper.selectById(order.getUserId());
            if (user == null || user.getWechatOpenid() == null || user.getWechatOpenid().isBlank()) {
                return;
            }
            String templateKey = switch (event) {
                case "shipped" -> "shipped";
                case "verified" -> "verified";
                default -> "refund";
            };
            wxMiniService.sendSubscribe(user.getWechatOpenid(), templateKey, title, content,
                    "pages/user/messages");
        } catch (Exception e) {
            log.warn("订阅消息下发失败 orderId={}: {}", order.getId(), e.getMessage());
        }
    }
}

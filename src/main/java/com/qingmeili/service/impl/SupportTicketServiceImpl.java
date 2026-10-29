package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.common.util.RandomUtil;
import com.qingmeili.entity.*;
import com.qingmeili.mapper.*;
import com.qingmeili.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 工单实现：一张工单 + 多条会话消息；商家与平台都能回复，平台可升级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketServiceImpl implements SupportTicketService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SupportTicketMapper ticketMapper;
    private final SupportTicketMessageMapper messageMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final UserMessageMapper userMessageMapper;

    @Override
    @Transactional
    public Result<SupportTicket> create(Long userId, Long orderId, String category, String title, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("请描述遇到的问题");
        }
        SupportTicket t = new SupportTicket();
        t.setTicketNo("T" + LocalDateTime.now().format(NO_FMT) + RandomUtil.randomNumbers(3));
        t.setUserId(userId);
        t.setOrderId(orderId);
        if (orderId != null) {
            Order order = orderMapper.selectById(orderId);
            if (order == null || !Objects.equals(order.getUserId(), userId)) {
                throw new BusinessException("关联订单不存在或不属于你");
            }
            t.setMerchantId(order.getMerchantId());
        }
        t.setCategory(category == null || category.isBlank() ? "OTHER" : category);
        t.setTitle(title == null || title.isBlank() ? "售后咨询" : title.trim());
        t.setStatus(0);
        t.setPriority(0);
        t.setDeleted(0);
        ticketMapper.insert(t);

        addMessage(t.getId(), "USER", displayName(userId), content.trim());
        return Result.ok("已提交，商家或平台会尽快回复", t);
    }

    @Override
    public Result<PageResult<Map<String, Object>>> listForUser(Long userId, Integer page, Integer pageSize) {
        return pageOf(new LambdaQueryWrapper<SupportTicket>()
                .eq(SupportTicket::getUserId, userId)
                .orderByDesc(SupportTicket::getUpdateTime), page, pageSize);
    }

    @Override
    public Result<PageResult<Map<String, Object>>> listForMerchant(Long merchantId, Integer status, Integer page, Integer pageSize) {
        LambdaQueryWrapper<SupportTicket> w = new LambdaQueryWrapper<SupportTicket>()
                .eq(SupportTicket::getMerchantId, merchantId)
                .ne(SupportTicket::getPriority, 1) // 已升级到平台的工单由平台处理
                .orderByDesc(SupportTicket::getUpdateTime);
        if (status != null) {
            w.eq(SupportTicket::getStatus, status);
        }
        return pageOf(w, page, pageSize);
    }

    @Override
    public Result<PageResult<Map<String, Object>>> listForAdmin(Integer status, Integer page, Integer pageSize) {
        LambdaQueryWrapper<SupportTicket> w = new LambdaQueryWrapper<SupportTicket>()
                .orderByDesc(SupportTicket::getPriority)
                .orderByDesc(SupportTicket::getUpdateTime);
        if (status != null) {
            w.eq(SupportTicket::getStatus, status);
        }
        return pageOf(w, page, pageSize);
    }

    @Override
    public Result<Map<String, Object>> detail(Long id) {
        SupportTicket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BusinessException("工单不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", t);
        data.put("messages", messageMapper.selectList(new LambdaQueryWrapper<SupportTicketMessage>()
                .eq(SupportTicketMessage::getTicketId, id)
                .orderByAsc(SupportTicketMessage::getCreateTime)));
        User user = userMapper.selectById(t.getUserId());
        data.put("userName", user == null ? "" : user.getNickname());
        Merchant merchant = t.getMerchantId() == null ? null : merchantMapper.selectById(t.getMerchantId());
        data.put("merchantName", merchant == null ? "" : merchant.getName());
        return Result.ok(data);
    }

    @Override
    @Transactional
    public Result<Void> reply(Long id, String senderType, Long senderId, String senderName, String content) {
        SupportTicket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BusinessException("工单不存在");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("回复内容不能为空");
        }
        if ("MERCHANT".equals(senderType) && !Objects.equals(t.getMerchantId(), senderId)) {
            return Result.fail("无权回复该工单");
        }
        if ("USER".equals(senderType) && !Objects.equals(t.getUserId(), senderId)) {
            return Result.fail("无权回复该工单");
        }
        addMessage(id, senderType, senderName, content.trim());
        if (t.getStatus() == null || t.getStatus() == 0) {
            t.setStatus(1);
        }
        t.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(t);
        if ("MERCHANT".equals(senderType) || "ADMIN".equals(senderType)) {
            pushUserNotice(t, senderType, content.trim());
        }
        return Result.ok("已回复", null);
    }

    @Override
    @Transactional
    public Result<Void> updateStatus(Long id, String operatorType, Long operatorId, int status, boolean escalate) {
        SupportTicket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BusinessException("工单不存在");
        }
        if ("MERCHANT".equals(operatorType) && !Objects.equals(t.getMerchantId(), operatorId)) {
            return Result.fail("无权操作该工单");
        }
        if ("USER".equals(operatorType) && !Objects.equals(t.getUserId(), operatorId)) {
            return Result.fail("无权操作该工单");
        }
        if (status < 0 || status > 3) {
            throw new BusinessException("状态不合法");
        }
        t.setStatus(status);
        if (escalate) {
            t.setPriority(1);
        }
        t.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(t);
        if ((status == 2 || status == 3) && !"USER".equals(operatorType)) {
            pushUserNotice(t, operatorType, status == 2 ? "你的工单已处理完成" : "你的工单已关闭");
        }
        return Result.ok("已更新", null);
    }

    private Result<PageResult<Map<String, Object>>> pageOf(LambdaQueryWrapper<SupportTicket> w, Integer page, Integer pageSize) {
        Page<SupportTicket> param = new Page<>(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        Page<SupportTicket> result = ticketMapper.selectPage(param, w);
        List<Map<String, Object>> list = result.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ticket", t);
            User u = userMapper.selectById(t.getUserId());
            m.put("userName", u == null ? "" : u.getNickname());
            m.put("lastMessage", lastMessage(t.getId()));
            return m;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    private String lastMessage(Long ticketId) {
        List<SupportTicketMessage> list = messageMapper.selectList(new LambdaQueryWrapper<SupportTicketMessage>()
                .eq(SupportTicketMessage::getTicketId, ticketId)
                .orderByDesc(SupportTicketMessage::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? "" : list.get(0).getContent();
    }

    private void addMessage(Long ticketId, String senderType, String senderName, String content) {
        SupportTicketMessage m = new SupportTicketMessage();
        m.setTicketId(ticketId);
        m.setSenderType(senderType);
        m.setSenderName(senderName == null ? "" : senderName);
        m.setContent(content);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

    private String displayName(Long userId) {
        User u = userMapper.selectById(userId);
        return u == null ? "游客" : u.getNickname();
    }

    /** 商家/平台回复与结案都写一条站内信，游客不必反复进工单页刷 */
    private void pushUserNotice(SupportTicket t, String senderType, String content) {
        try {
            UserMessage m = new UserMessage();
            m.setUserId(t.getUserId());
            m.setType("SUPPORT");
            m.setOrderId(t.getOrderId());
            String who = "ADMIN".equals(senderType) ? "平台" : "商家";
            m.setTitle("工单有新回复：" + t.getTicketNo());
            m.setTitleEn("New reply on ticket " + t.getTicketNo());
            m.setContent(who + "回复：" + (content.length() > 60 ? content.substring(0, 60) + "…" : content));
            m.setContentEn("Reply from " + ("ADMIN".equals(senderType) ? "the platform" : "the merchant")
                    + ": " + (content.length() > 60 ? content.substring(0, 60) + "…" : content));
            m.setReadFlag(0);
            m.setDeleted(0);
            m.setCreateTime(LocalDateTime.now());
            userMessageMapper.insert(m);
        } catch (Exception e) {
            log.warn("工单通知写入失败 ticketId={}: {}", t.getId(), e.getMessage());
        }
    }
}

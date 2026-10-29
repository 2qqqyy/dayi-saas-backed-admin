package com.qingmeili.service;

import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.SupportTicket;

import java.util.Map;

/**
 * 客服 / 售后 / 纠纷工单
 */
public interface SupportTicketService {

    Result<SupportTicket> create(Long userId, Long orderId, String category, String title, String content);

    Result<PageResult<Map<String, Object>>> listForUser(Long userId, Integer page, Integer pageSize);

    Result<PageResult<Map<String, Object>>> listForMerchant(Long merchantId, Integer status, Integer page, Integer pageSize);

    Result<PageResult<Map<String, Object>>> listForAdmin(Integer status, Integer page, Integer pageSize);

    Result<Map<String, Object>> detail(Long id);

    /** senderType: USER / MERCHANT / ADMIN */
    Result<Void> reply(Long id, String senderType, Long senderId, String senderName, String content);

    /** 商家或平台更新状态；返回 false 表示无权限 */
    Result<Void> updateStatus(Long id, String operatorType, Long operatorId, int status, boolean escalate);
}

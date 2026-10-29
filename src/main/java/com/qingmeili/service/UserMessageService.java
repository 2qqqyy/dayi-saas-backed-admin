package com.qingmeili.service;

import com.qingmeili.entity.Order;

/**
 * 游客站内消息：订单关键事件主动写入，游客端消息中心消费
 */
public interface UserMessageService {

    /**
     * 订单事件通知。event 取值：shipped / refunded / refund_rejected / verified / cancelled
     * 内部吞异常：通知失败不能回滚主业务事务
     */
    void notifyOrderEvent(Order order, String event);
}

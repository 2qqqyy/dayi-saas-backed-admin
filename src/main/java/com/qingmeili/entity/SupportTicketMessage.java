package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单会话消息
 */
@Data
@TableName("support_ticket_message")
public class SupportTicketMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    /** USER / MERCHANT / ADMIN */
    private String senderType;

    private String senderName;

    private String content;

    private LocalDateTime createTime;
}

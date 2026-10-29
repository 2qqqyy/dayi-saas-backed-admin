package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游客站内消息：订单事件（发货/退款/核销）主动触达，替代此前"游客对订单进展零感知"
 */
@Data
@TableName("user_message")
public class UserMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** ORDER / REFUND / VERIFY */
    private String type;

    private Long orderId;

    private String title;

    private String titleEn;

    private String content;

    private String contentEn;

    private Integer readFlag;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
}

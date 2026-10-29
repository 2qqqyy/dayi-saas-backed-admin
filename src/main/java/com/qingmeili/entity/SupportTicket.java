package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服 / 售后 / 纠纷工单
 */
@Data
@TableName("support_ticket")
public class SupportTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;

    private Long userId;

    private Long merchantId;

    private Long orderId;

    /** AFTER_SALE / REFUND / COMPLAINT / OTHER */
    private String category;

    private String title;

    /** 0待处理 1处理中 2已解决 3已关闭 */
    private Integer status;

    /** 0普通 1平台升级 */
    private Integer priority;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

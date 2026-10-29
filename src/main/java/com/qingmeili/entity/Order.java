package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long merchantId;

    private Integer type;

    private String status;

    private String title;

    private String subtitle;

    private String coverImage;

    private BigDecimal amount;

    private BigDecimal payAmount;

    private LocalDateTime payTime;

    private String payMethod;

    private String transactionId;

    private String cancelReason;

    private LocalDateTime cancelTime;

    private BigDecimal refundAmount;

    private LocalDateTime refundTime;

    private String refundNo;

    private String refundReason;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

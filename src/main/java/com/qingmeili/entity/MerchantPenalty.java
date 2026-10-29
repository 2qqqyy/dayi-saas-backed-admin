package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家违规处罚
 */
@Data
@TableName("merchant_penalty")
public class MerchantPenalty {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** WARNING / DEDUCT / SUSPEND */
    private String type;

    private Integer points;

    private String reason;

    private String source;

    /** 1生效 0已撤销 */
    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String operator;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
}

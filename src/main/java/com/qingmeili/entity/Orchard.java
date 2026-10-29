package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orchard")
public class Orchard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String name;
    private String nameEn;

    private String address;
    private String addressEn;

    private BigDecimal lat;

    private BigDecimal lng;

    private String coverImage;

    private BigDecimal area;

    private String intro;

    private String notices;
    private String noticesEn;

    private String refundRule;
    private String refundRuleEn;

    private BigDecimal rating;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private String summaryEn;
}

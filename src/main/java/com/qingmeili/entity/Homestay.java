package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("homestay")
public class Homestay {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String name;
    private String nameEn;

    private String address;

    private BigDecimal lat;

    private BigDecimal lng;

    private String coverImage;

    private String images;

    private String facilities;

    private String tags;

    private String intro;

    private BigDecimal rating;

    private Integer auditStatus;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private String summaryEn;
}

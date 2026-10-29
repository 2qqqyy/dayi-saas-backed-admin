package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private Long categoryId;

    private String name;
    private String nameEn;

    private String coverImage;

    private String images;

    private String summary;
    private String summaryEn;

    private String detail;

    private BigDecimal price;

    private String origin;

    private Integer sales;

    private Integer auditStatus;

    private String auditRemark;

    private Integer onShelf;

    /** 定时上架时间，到点由任务自动上架 */
    private java.time.LocalDateTime onShelfTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

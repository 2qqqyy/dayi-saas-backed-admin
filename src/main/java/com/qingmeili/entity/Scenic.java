package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景点/点位表
 */
@Data
@TableName("scenic")
public class Scenic {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外编码，兼容 C 端历史链接中的 scenic-1 形式 */
    private String code;

    private String name;
    private String nameEn;

    private String category;

    private String coverImage;

    private String location;

    private String distance;

    private BigDecimal lat;

    private BigDecimal lng;

    private String summary;
    private String summaryEn;

    private String intro;

    private String openTime;

    private String phone;

    private String audioText;

    private String audioUrl;

    private String audioTextEn;

    private String audioUrlEn;

    private BigDecimal rating;

    private String tags;

    private Integer sort;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

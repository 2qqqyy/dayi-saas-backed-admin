package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 攻略内容表（管理端内容运营）
 */
@Data
@TableName("guide")
public class Guide {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String titleEn;

    private String author;

    private String cover;

    private String content;

    private Integer viewCount;

    private LocalDateTime publishTime;

    /** 0草稿 1已发布 2已下架 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private String summaryEn;
}

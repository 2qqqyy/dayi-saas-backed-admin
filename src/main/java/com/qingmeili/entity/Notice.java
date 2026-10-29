package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告表
 */
@Data
@TableName("notice")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String titleEn;

    private String summary;
    private String summaryEn;

    private String content;
    private String contentEn;

    /** NOTICE 村务提醒 / SERVICE 服务通知 / ACTIVITY 村内活动 */
    private String category;

    /** ALL 面向全体 / USER 面向游客 / MERCHANT 面向商家 */
    private String audience;

    /** 1 置顶 0 普通 */
    private Integer pinned;

    /** 1 已发布 0 草稿 */
    private Integer status;

    private LocalDateTime publishTime;

    private LocalDateTime expireTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

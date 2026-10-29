package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告已读表：按游客/商家分别记录
 */
@Data
@TableName("notice_read")
public class NoticeRead {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noticeId;

    /** user 游客 / merchant 商家 */
    private String ownerType;

    private Long ownerId;

    private LocalDateTime readTime;
}

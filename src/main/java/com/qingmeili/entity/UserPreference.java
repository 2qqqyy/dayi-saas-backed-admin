package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 显示偏好：主题 + 长辈模式，游客/商家共用
 */
@Data
@TableName("user_preference")
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** user 游客 / merchant 商家 */
    private String ownerType;

    private Long ownerId;

    private String themeCode;

    /** 1 开 0 关 */
    private Integer elderMode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

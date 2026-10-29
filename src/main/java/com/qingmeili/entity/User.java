package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表（C端游客）
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.qingmeili.common.util.MaskSerializers.Mobile.class)
    private String mobile;

    private String passwordHash;

    private String nickname;

    private String avatar;

    private String level;

    private String wechatOpenid;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员等级配置
 */
@Data
@TableName("user_level")
public class UserLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String threshold;

    private BigDecimal minAmount;

    private Integer sort;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

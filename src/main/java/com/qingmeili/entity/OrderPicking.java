package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("order_picking")
public class OrderPicking {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long sessionId;

    private Long orchardId;

    private Integer peopleNum;

    private String contactName;

    private String contactMobile;

    private String verifyCode;

    private LocalDateTime verifyTime;

    private LocalDate pickDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

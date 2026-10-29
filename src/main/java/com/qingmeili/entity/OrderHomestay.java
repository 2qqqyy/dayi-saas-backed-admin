package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("order_homestay")
public class OrderHomestay {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long homestayId;

    private Long roomId;

    private LocalDate checkIn;

    private LocalDate checkOut;

    private Integer nights;

    private String guestName;

    private String guestMobile;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

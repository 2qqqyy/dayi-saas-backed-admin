package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("room_calendar")
public class RoomCalendar {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private LocalDate roomDate;

    private Integer stock;

    private BigDecimal price;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("picking_session")
public class PickingSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private Long orchardId;

    private LocalDate slotDate;

    private String timeRange;

    private Integer maxPeople;

    private Integer bookedCount;

    private BigDecimal price;

    private Integer auditStatus;

    private String auditRemark;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

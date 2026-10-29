package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("homestay_room")
public class HomestayRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long homestayId;

    private String name;

    private String image;

    private Integer capacity;

    private String bedType;

    private String breakfast;

    private BigDecimal price;

    private Integer stock;

    private Integer area;

    private Integer status;

    /** 平台审核：0待审 1通过 2驳回 */
    private Integer auditStatus;

    private String auditRemark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

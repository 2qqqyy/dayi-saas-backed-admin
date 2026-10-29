package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("village")
public class Village {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String location;

    private BigDecimal lat;

    private BigDecimal lng;

    private String coverImage;

    private String intro;

    private BigDecimal area;

    private Integer population;

    private String highlights;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

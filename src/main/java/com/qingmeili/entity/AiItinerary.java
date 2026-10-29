package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 行程生成记录
 */
@Data
@TableName("ai_itinerary")
public class AiItinerary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String userName;

    private String title;

    private String travelDate;

    private String type;

    private Integer people;

    /** 途经点名称，逗号分隔 */
    private String spots;

    /** 完整行程 JSON */
    private String content;

    private Integer saved;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

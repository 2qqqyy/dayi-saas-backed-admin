package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 旅游线路表
 */
@Data
@TableName("travel_route")
public class TravelRoute {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外编码，兼容 C 端历史链接中的 route-1 形式 */
    private String code;

    private String title;
    private String titleEn;

    private String subtitle;

    private String coverImage;

    private String duration;

    private String distance;

    private String difficulty;

    private String bestTime;

    private String summary;
    private String summaryEn;

    private String stops;

    /** 富明细 JSON：suits / highlights / includes / tips / itinerary */
    private String detail;

    private Integer sort;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

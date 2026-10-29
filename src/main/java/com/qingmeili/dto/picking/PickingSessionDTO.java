package com.qingmeili.dto.picking;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘档期DTO
 */
@Data
public class PickingSessionDTO {

    @NotNull(message = "果园ID不能为空")
    private Long orchardId;

    @NotNull(message = "预约日期不能为空")
    private LocalDate slotDate;

    private String timeRange;

    private Integer maxPeople;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    /** 场次状态：0=草稿 1=已发布（不传默认已发布） */
    private Integer status;
}

package com.qingmeili.dto.homestay;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 房态日历DTO
 */
@Data
public class RoomCalendarDTO {

    @NotEmpty(message = "日期列表不能为空")
    private List<LocalDate> dates;

    @NotNull(message = "状态不能为空")
    private Integer status;

    private java.math.BigDecimal price;

    private Integer stock;
}

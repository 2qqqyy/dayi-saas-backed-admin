package com.qingmeili.dto.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

/**
 * 民宿订单DTO
 */
@Data
public class HomestayOrderDTO {

    @NotNull(message = "民宿ID不能为空")
    private Long homestayId;

    @NotNull(message = "房型ID不能为空")
    private Long roomId;

    @NotNull(message = "入住日期不能为空")
    private LocalDate checkIn;

    @NotNull(message = "退房日期不能为空")
    private LocalDate checkOut;

    private String guestName;

    private String guestMobile;
}

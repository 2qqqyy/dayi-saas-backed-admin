package com.qingmeili.dto.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

/**
 * 采摘订单DTO
 */
@Data
public class PickingOrderDTO {

    @NotNull(message = "果园ID不能为空")
    private Long orchardId;

    @NotNull(message = "档期ID不能为空")
    private Long sessionId;

    @NotNull(message = "采摘日期不能为空")
    private LocalDate date;

    @NotNull(message = "采摘人数不能为空")
    private Integer peopleNum;

    private String contactName;

    private String contactMobile;
}

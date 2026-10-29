package com.qingmeili.dto.homestay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 民宿房型DTO
 */
@Data
public class HomestayRoomDTO {

    @NotNull(message = "民宿ID不能为空")
    private Long homestayId;

    @NotBlank(message = "房型名称不能为空")
    private String name;

    private String image;

    private Integer capacity;

    private String bedType;

    private String breakfast;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    private Integer stock;

    private Integer area;

    private Integer status;
}

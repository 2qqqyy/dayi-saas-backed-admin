package com.qingmeili.dto.picking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 果园DTO
 */
@Data
public class OrchardDTO {

    @NotBlank(message = "果园名称不能为空")
    private String name;

    @NotBlank(message = "地址不能为空")
    private String address;

    private BigDecimal lat;

    private BigDecimal lng;

    private String coverImage;

    private BigDecimal area;

    private String intro;

    private String notices;

    private String refundRule;

    private Integer status;
}

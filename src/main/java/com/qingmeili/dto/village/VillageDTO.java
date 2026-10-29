package com.qingmeili.dto.village;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 乡村DTO
 */
@Data
public class VillageDTO {

    @NotBlank(message = "乡村名称不能为空")
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
}

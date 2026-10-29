package com.qingmeili.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品DTO
 */
@Data
public class ProductDTO {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private Long categoryId;

    private String coverImage;

    private String images;

    private String summary;

    private String detail;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    private String origin;

    private List<SkuDTO> skus;
}

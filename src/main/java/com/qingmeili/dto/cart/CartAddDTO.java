package com.qingmeili.dto.cart;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 购物车添加DTO
 */
@Data
public class CartAddDTO {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @NotNull(message = "数量不能为空")
    private Integer quantity;
}

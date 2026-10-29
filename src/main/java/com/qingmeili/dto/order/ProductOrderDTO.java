package com.qingmeili.dto.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 商品订单DTO
 */
@Data
public class ProductOrderDTO {

    @NotNull(message = "收货地址ID不能为空")
    private Long addressId;

    /**
     * 购物车项ID列表（从购物车下单时使用）
     */
    private List<Long> cartItemIds;

    /**
     * 直接下单的商品列表（不走购物车时使用）
     */
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO {
        private Long productId;
        private Long skuId;
        private Integer quantity;
    }
}

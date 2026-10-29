package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.cart.CartAddDTO;
import com.qingmeili.entity.CartItem;

import java.util.List;
import java.util.Map;

/**
 * 购物车服务
 */
public interface CartService extends IService<CartItem> {

    Result<List<Map<String, Object>>> list(Long userId);

    Result<CartItem> add(Long userId, CartAddDTO dto);

    Result<Void> updateQuantity(Long userId, Long id, Integer quantity);

    Result<Void> updateSelect(Long userId, Long id, Integer selected);

    Result<Void> selectAll(Long userId, Integer selected);

    Result<Void> remove(Long userId, List<Long> ids);

    Result<Void> clearSelected(Long userId);

    Result<Void> clearAll(Long userId);
}

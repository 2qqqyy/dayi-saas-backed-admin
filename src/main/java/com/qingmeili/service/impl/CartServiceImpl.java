package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.cart.CartAddDTO;
import com.qingmeili.entity.CartItem;
import com.qingmeili.entity.Product;
import com.qingmeili.entity.ProductSku;
import com.qingmeili.mapper.CartItemMapper;
import com.qingmeili.mapper.ProductMapper;
import com.qingmeili.mapper.ProductSkuMapper;
import com.qingmeili.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车服务实现
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartItemMapper, CartItem> implements CartService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    @Override
    public Result<List<Map<String, Object>>> list(Long userId) {
        List<CartItem> items = baseMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .orderByDesc(CartItem::getCreateTime));
        List<Map<String, Object>> result = items.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("productId", item.getProductId());
            map.put("skuId", item.getSkuId());
            map.put("quantity", item.getQuantity());
            map.put("selected", item.getSelected());

            Product product = productMapper.selectById(item.getProductId());
            ProductSku sku = productSkuMapper.selectById(item.getSkuId());
            if (product != null) {
                map.put("productName", product.getName());
                map.put("image", product.getCoverImage());
            }
            if (sku != null) {
                map.put("skuName", sku.getName());
                map.put("price", sku.getPrice());
                map.put("stock", sku.getStock() != null ? sku.getStock() : 0);
            }
            return map;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @Override
    @Transactional
    public Result<CartItem> add(Long userId, CartAddDTO dto) {
        // 检查是否已存在同一SKU
        CartItem existing = baseMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSkuId, dto.getSkuId()));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + dto.getQuantity());
            baseMapper.updateById(existing);
            return Result.ok("数量已更新", existing);
        }
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(dto.getProductId());
        item.setSkuId(dto.getSkuId());
        item.setQuantity(dto.getQuantity());
        item.setSelected(1);
        baseMapper.insert(item);
        return Result.ok("添加成功", item);
    }

    @Override
    public Result<Void> updateQuantity(Long userId, Long id, Integer quantity) {
        CartItem item = requireOwned(userId, id);
        if (quantity == null || quantity < 1 || quantity > 999) {
            throw new BusinessException("数量需在 1-999 之间");
        }
        item.setQuantity(quantity);
        baseMapper.updateById(item);
        return Result.ok("更新成功", null);
    }

    @Override
    public Result<Void> updateSelect(Long userId, Long id, Integer selected) {
        CartItem item = requireOwned(userId, id);
        item.setSelected(selected == null || selected == 0 ? 0 : 1);
        baseMapper.updateById(item);
        return Result.ok("更新成功", null);
    }

    /** 购物车行属于当前用户才允许改，否则前端传别人的 id 就能改别人的数据 */
    private CartItem requireOwned(Long userId, Long id) {
        CartItem item = baseMapper.selectById(id);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException("购物车项不存在");
        }
        return item;
    }

    @Override
    public Result<Void> selectAll(Long userId, Integer selected) {
        CartItem updateEntity = new CartItem();
        updateEntity.setSelected(selected);
        baseMapper.update(updateEntity,
                new LambdaUpdateWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId));
        return Result.ok("更新成功", null);
    }

    @Override
    @Transactional
    public Result<Void> remove(Long userId, List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            baseMapper.deleteOwned(userId, ids);
        }
        return Result.ok("删除成功", null);
    }

    @Override
    public Result<Void> clearSelected(Long userId) {
        baseMapper.deleteSelectedOwned(userId);
        return Result.ok("清除成功", null);
    }

    @Override
    public Result<Void> clearAll(Long userId) {
        baseMapper.deleteAllOwned(userId);
        return Result.ok("清除成功", null);
    }
}

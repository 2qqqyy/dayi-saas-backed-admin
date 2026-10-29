package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Product;
import com.qingmeili.entity.ProductCategory;
import com.qingmeili.entity.ProductSku;
import com.qingmeili.entity.Review;
import com.qingmeili.mapper.ProductCategoryMapper;
import com.qingmeili.mapper.ProductSkuMapper;
import com.qingmeili.service.ProductService;
import com.qingmeili.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C端 - 商品（公开接口）
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCategoryMapper productCategoryMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ReviewService reviewService;

    @GetMapping("/categories")
    public Result<List<ProductCategory>> categories() {
        List<ProductCategory> list = productCategoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategory>()
                        .eq(ProductCategory::getStatus, 1)
                        .orderByAsc(ProductCategory::getSort));
        return Result.ok(list);
    }

    @GetMapping("/list")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return productService.page(page, pageSize, resolveCategoryIds(category), keyword, 1);
    }

    /** H5 别名：GET /api/product */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> listAlias(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        return productService.page(page, pageSize, resolveCategoryIds(category), keyword, 1);
    }

    /**
     * category 既可能是分类 id 也可能是分类名（H5 分类页传名称），统一解析成
     * 「自身 + 子分类」的 id 集合；名称匹配不到时返回 -1，让结果为空而不是报错。
     */
    private List<Long> resolveCategoryIds(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        String key = category.trim();
        Long selfId;
        if (key.matches("\\d+")) {
            selfId = Long.valueOf(key);
        } else {
            ProductCategory hit = productCategoryMapper.selectOne(
                    new LambdaQueryWrapper<ProductCategory>()
                            .eq(ProductCategory::getName, key)
                            .orderByAsc(ProductCategory::getId)
                            .last("limit 1"));
            if (hit == null) {
                return List.of(-1L);
            }
            selfId = hit.getId();
        }
        List<Long> ids = new ArrayList<>();
        ids.add(selfId);
        productCategoryMapper.selectList(
                        new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getParentId, selfId))
                .forEach(c -> ids.add(c.getId()));
        return ids;
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return productService.getDetail(id);
    }

    /** 商品评价：C 端商品详情页展示真实评价，不再固定「用户评价（0）」 */
    @GetMapping("/{id}/reviews")
    public Result<PageResult<Review>> reviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return reviewService.listByProduct(id, page, pageSize);
    }
}

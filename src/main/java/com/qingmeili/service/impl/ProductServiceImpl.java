package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.product.ProductDTO;
import com.qingmeili.dto.product.SkuDTO;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.Product;
import com.qingmeili.entity.ProductCategory;
import com.qingmeili.entity.ProductSku;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.ProductCategoryMapper;
import com.qingmeili.mapper.ProductMapper;
import com.qingmeili.mapper.ProductSkuMapper;
import com.qingmeili.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品服务实现
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final ProductSkuMapper productSkuMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final MerchantMapper merchantMapper;

    @Override
    public Result<PageResult<Map<String, Object>>> page(Integer page, Integer pageSize, List<Long> categoryIds, String keyword, Integer onShelf) {
        Page<Product> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (categoryIds != null && !categoryIds.isEmpty()) {
            wrapper.in(Product::getCategoryId, categoryIds);
        }
        applyKeyword(wrapper, keyword);
        if (onShelf != null) {
            wrapper.eq(Product::getOnShelf, onShelf);
        }
        wrapper.orderByDesc(Product::getCreateTime);
        Page<Product> result = baseMapper.selectPage(pageParam, wrapper);
        // 为每个商品加载SKU列表
        Map<Long, String> merchantNames = loadMerchantNames(result.getRecords());
        List<Map<String, Object>> list = result.getRecords().stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("product", p);
            map.put("skus", productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, p.getId())));
            map.put("merchantName", merchantNames.get(p.getMerchantId()));
            return map;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    private Map<Long, String> loadMerchantNames(List<Product> products) {
        List<Long> ids = products.stream()
                .map(Product::getMerchantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return merchantMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Merchant::getId, Merchant::getName, (a, b) -> a));
    }

    @Override
    public Result<PageResult<Map<String, Object>>> pageByMerchant(Long merchantId, Integer page, Integer pageSize, Long categoryId, String keyword, Integer onShelf) {
        Page<Product> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 关键：按当前登录商户隔离
        wrapper.eq(Product::getMerchantId, merchantId);
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        applyKeyword(wrapper, keyword);
        if (onShelf != null) {
            wrapper.eq(Product::getOnShelf, onShelf);
        }
        wrapper.orderByDesc(Product::getCreateTime);
        Page<Product> result = baseMapper.selectPage(pageParam, wrapper);
        List<Map<String, Object>> list = result.getRecords().stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("product", p);
            map.put("skus", productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, p.getId())));
            return map;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /**
     * 关键词搜索：按空格分词，词与词之间取交集；每个词匹配 名称 / 简介 / 产地 / 分类名。
     * 分类名参与匹配是为了让「新鲜水果」这类按分类找货的查询能命中其子分类下的商品。
     */
    private void applyKeyword(LambdaQueryWrapper<Product> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        List<ProductCategory> categories = productCategoryMapper.selectList(null);
        for (String token : keyword.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            List<Long> categoryIds = matchCategoryIds(categories, token);
            wrapper.and(w -> {
                w.like(Product::getName, token)
                        .or().like(Product::getSummary, token)
                        .or().like(Product::getOrigin, token);
                if (!categoryIds.isEmpty()) {
                    w.or().in(Product::getCategoryId, categoryIds);
                }
            });
        }
    }

    /** 名称命中该词的分类，连同其全部子孙分类的 id */
    private List<Long> matchCategoryIds(List<ProductCategory> categories, String token) {
        List<Long> matched = new ArrayList<>();
        for (ProductCategory c : categories) {
            if (c.getName() != null && c.getName().contains(token)) {
                matched.add(c.getId());
            }
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ProductCategory c : categories) {
                if (!matched.contains(c.getId()) && matched.contains(c.getParentId())) {
                    matched.add(c.getId());
                    grew = true;
                }
            }
        }
        return matched;
    }

    @Override
    public Result<Map<String, Object>> getDetail(Long id) {
        Product product = baseMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        Map<String, Object> data = new HashMap<>();
        data.put("product", product);
        data.put("skus", skus);
        if (product.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(product.getMerchantId());
            if (merchant != null) {
                data.put("merchantName", merchant.getName());
                Map<String, Object> brief = new HashMap<>();
                brief.put("id", merchant.getId());
                brief.put("name", merchant.getName());
                brief.put("rating", merchant.getRating());
                brief.put("intro", merchant.getIntro());
                brief.put("address", merchant.getAddress());
                brief.put("openTime", merchant.getOpenTime());
                brief.put("shopNotice", merchant.getShopNotice());
                data.put("merchant", brief);
            }
        }
        return Result.ok(data);
    }

    @Override
    @Transactional
    public Result<Product> create(ProductDTO dto) {
        Product product = new Product();
        product.setMerchantId(UserContext.getMerchantId());
        product.setCategoryId(dto.getCategoryId());
        product.setName(dto.getName());
        product.setCoverImage(dto.getCoverImage());
        product.setImages(dto.getImages());
        product.setSummary(dto.getSummary());
        product.setDetail(dto.getDetail());
        product.setPrice(dto.getPrice());
        product.setOrigin(dto.getOrigin());
        product.setSales(0);
        product.setAuditStatus(0);
        product.setOnShelf(0);
        baseMapper.insert(product);

        // 创建SKU
        if (dto.getSkus() != null && !dto.getSkus().isEmpty()) {
            for (SkuDTO skuDto : dto.getSkus()) {
                ProductSku sku = new ProductSku();
                sku.setProductId(product.getId());
                sku.setName(skuDto.getName());
                sku.setPrice(skuDto.getPrice());
                sku.setStock(skuDto.getStock());
                sku.setSales(0);
                productSkuMapper.insert(sku);
            }
        }
        return Result.ok("创建成功", product);
    }

    @Override
    @Transactional
    public Result<Product> update(Long id, ProductDTO dto) {
        Product product = baseMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (dto.getName() != null) product.setName(dto.getName());
        if (dto.getCategoryId() != null) product.setCategoryId(dto.getCategoryId());
        if (dto.getCoverImage() != null) product.setCoverImage(dto.getCoverImage());
        if (dto.getImages() != null) product.setImages(dto.getImages());
        if (dto.getSummary() != null) product.setSummary(dto.getSummary());
        if (dto.getDetail() != null) product.setDetail(dto.getDetail());
        if (dto.getPrice() != null) product.setPrice(dto.getPrice());
        if (dto.getOrigin() != null) product.setOrigin(dto.getOrigin());
        baseMapper.updateById(product);

        // 更新SKU：先删后建
        if (dto.getSkus() != null) {
            productSkuMapper.delete(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
            for (SkuDTO skuDto : dto.getSkus()) {
                ProductSku sku = new ProductSku();
                sku.setProductId(id);
                sku.setName(skuDto.getName());
                sku.setPrice(skuDto.getPrice());
                sku.setStock(skuDto.getStock());
                sku.setSales(0);
                productSkuMapper.insert(sku);
            }
        }
        return Result.ok("修改成功", product);
    }

    @Override
    public Result<Void> updateStatus(Long id, Integer onShelf) {
        Product product = baseMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        product.setOnShelf(onShelf);
        baseMapper.updateById(product);
        return Result.ok("状态更新成功", null);
    }

    @Override
    public Result<Void> audit(Long id, String action, String reason) {
        Product product = baseMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if ("approve".equals(action)) {
            product.setAuditStatus(1);
            product.setOnShelf(1);
        } else if ("reject".equals(action)) {
            product.setAuditStatus(2);
        } else {
            throw new BusinessException("无效的操作类型");
        }
        baseMapper.updateById(product);
        return Result.ok("审核完成", null);
    }
}

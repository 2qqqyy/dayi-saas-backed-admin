package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.*;
import com.qingmeili.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台管理 - 商品 / SKU / 分类
 */
@RestController
@RequestMapping("/api/admin")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminProductManageController {

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final MerchantMapper merchantMapper;

    /* ---------------- 商品 ---------------- */

    @GetMapping("/products")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer onShelf,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Product> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) wrapper.like(Product::getName, keyword);
        if (categoryId != null) wrapper.eq(Product::getCategoryId, categoryId);
        if (auditStatus != null) wrapper.eq(Product::getAuditStatus, auditStatus);
        if (onShelf != null) wrapper.eq(Product::getOnShelf, onShelf);
        wrapper.orderByDesc(Product::getUpdateTime);
        Page<Product> result = productMapper.selectPage(pageParam, wrapper);

        Map<Long, String> categoryNames = productCategoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
        Map<Long, String> merchantNames = merchantMapper.selectList(null).stream()
                .collect(Collectors.toMap(Merchant::getId, Merchant::getName, (a, b) -> a));

        List<Map<String, Object>> list = result.getRecords().stream().map(p -> {
            List<ProductSku> skus = productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, p.getId()));
            int stock = skus.stream().mapToInt(s -> s.getStock() != null ? s.getStock() : 0).sum();
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("image", p.getCoverImage());
            m.put("summary", p.getSummary());
            m.put("categoryId", p.getCategoryId());
            m.put("category", categoryNames.getOrDefault(p.getCategoryId(), ""));
            m.put("merchantId", p.getMerchantId());
            m.put("merchantName", merchantNames.getOrDefault(p.getMerchantId(), ""));
            m.put("price", p.getPrice());
            m.put("stock", stock);
            m.put("sales", p.getSales() != null ? p.getSales() : 0);
            m.put("auditStatus", p.getAuditStatus());
            m.put("onShelf", p.getOnShelf());
            m.put("updatedAt", p.getUpdateTime());
            return m;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    @PostMapping("/products")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Product p = new Product();
        p.setName(String.valueOf(body.getOrDefault("name", "")));
        if (!StringUtils.hasText(p.getName())) throw new BusinessException("商品名称不能为空");
        p.setSummary((String) body.get("summary"));
        p.setCoverImage((String) body.get("image"));
        if (body.get("categoryId") != null) p.setCategoryId(Long.valueOf(String.valueOf(body.get("categoryId"))));
        if (body.get("merchantId") != null) p.setMerchantId(Long.valueOf(String.valueOf(body.get("merchantId"))));
        if (body.get("price") != null) p.setPrice(new java.math.BigDecimal(String.valueOf(body.get("price"))));
        p.setSales(0);
        p.setAuditStatus(1);
        p.setOnShelf(1);
        productMapper.insert(p);

        int stock = body.get("stock") != null ? Integer.parseInt(String.valueOf(body.get("stock"))) : 0;
        ProductSku sku = new ProductSku();
        sku.setProductId(p.getId());
        sku.setName("默认规格");
        sku.setPrice(p.getPrice());
        sku.setStock(stock);
        sku.setSales(0);
        productSkuMapper.insert(sku);

        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        return Result.ok("创建成功", m);
    }

    @PutMapping("/products/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException("商品不存在");
        if (body.get("name") != null) p.setName(String.valueOf(body.get("name")));
        if (body.get("summary") != null) p.setSummary(String.valueOf(body.get("summary")));
        if (body.get("image") != null) p.setCoverImage(String.valueOf(body.get("image")));
        if (body.get("categoryId") != null) p.setCategoryId(Long.valueOf(String.valueOf(body.get("categoryId"))));
        if (body.get("price") != null) p.setPrice(new java.math.BigDecimal(String.valueOf(body.get("price"))));
        productMapper.updateById(p);
        return Result.ok("更新成功", null);
    }

    /**
     * 上架/下架与审核状态：onShelf 1/0，auditStatus 0待审核/1通过/2驳回
     * 兼容查询参数与请求体两种传参，body 优先；只持久化实际提供的字段
     */
    @PutMapping("/products/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam(required = false) Integer onShelf,
                                     @RequestParam(required = false) Integer auditStatus,
                                     @RequestBody(required = false) Map<String, Object> body) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException("商品不存在");
        if (body != null) {
            if (body.get("onShelf") != null) onShelf = Integer.valueOf(String.valueOf(body.get("onShelf")));
            if (body.get("auditStatus") != null) auditStatus = Integer.valueOf(String.valueOf(body.get("auditStatus")));
        }
        if (onShelf != null) p.setOnShelf(onShelf);
        if (auditStatus != null) p.setAuditStatus(auditStatus);
        if (body != null && body.get("auditRemark") != null) {
            p.setAuditRemark(String.valueOf(body.get("auditRemark")));
        }
        // 重新提交审核时清掉旧驳回原因，避免商家看到过期信息
        if (auditStatus != null && auditStatus != 2) {
            p.setAuditRemark(null);
        }
        productMapper.updateById(p);
        return Result.ok("状态更新成功", null);
    }

    /** 库存调整：写入该商品第一个 SKU（无 SKU 则创建默认 SKU） */
    @PutMapping("/products/{id}/stock")
    public Result<Void> updateStock(@PathVariable Long id, @RequestParam Integer stock) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException("商品不存在");
        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id).orderByAsc(ProductSku::getId));
        if (skus.isEmpty()) {
            ProductSku sku = new ProductSku();
            sku.setProductId(id);
            sku.setName("默认规格");
            sku.setPrice(p.getPrice());
            sku.setStock(stock);
            sku.setSales(0);
            productSkuMapper.insert(sku);
        } else {
            ProductSku first = skus.get(0);
            first.setStock(stock);
            productSkuMapper.updateById(first);
        }
        return Result.ok("库存已更新", null);
    }

    @DeleteMapping("/products/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException("商品不存在");
        productMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }

    /* ---------------- SKU ---------------- */

    @GetMapping("/products/{id}/skus")
    public Result<List<ProductSku>> skus(@PathVariable Long id) {
        return Result.ok(productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id)));
    }

    /** SKU 列表：附带商品名，后台 SKU 页无需再为每一行反查商品 */
    @GetMapping("/skus")
    public Result<List<Map<String, Object>>> allSkus(@RequestParam(required = false) Long productId) {
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) wrapper.eq(ProductSku::getProductId, productId);
        List<ProductSku> skus = productSkuMapper.selectList(wrapper.orderByAsc(ProductSku::getProductId));
        List<Long> productIds = skus.stream().map(ProductSku::getProductId).distinct().toList();
        Map<Long, String> names = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
        return Result.ok(skus.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("productId", s.getProductId());
            m.put("productName", names.getOrDefault(s.getProductId(), ""));
            m.put("name", s.getName());
            m.put("price", s.getPrice());
            m.put("stock", s.getStock());
            m.put("sales", s.getSales());
            m.put("updateTime", s.getUpdateTime());
            return m;
        }).toList());
    }

    /**
     * 编辑单个 SKU：规格名 / 价格 / 库存
     * product 表本身没有库存列，库存只存在 product_sku，改这里即三端生效
     */
    @PutMapping("/skus/{id}")
    public Result<Void> updateSku(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ProductSku sku = productSkuMapper.selectById(id);
        if (sku == null) throw new BusinessException("SKU 不存在");
        if (body != null && body.get("name") != null) {
            String name = String.valueOf(body.get("name")).trim();
            if (!StringUtils.hasText(name)) throw new BusinessException("规格名称不能为空");
            sku.setName(name);
        }
        if (body != null && body.get("price") != null) {
            java.math.BigDecimal price;
            try {
                price = new java.math.BigDecimal(String.valueOf(body.get("price")));
            } catch (NumberFormatException e) {
                throw new BusinessException("价格格式不正确");
            }
            if (price.compareTo(java.math.BigDecimal.ZERO) <= 0) throw new BusinessException("价格必须大于 0");
            sku.setPrice(price);
        }
        if (body != null && body.get("stock") != null) {
            int stock;
            try {
                stock = Integer.parseInt(String.valueOf(body.get("stock")));
            } catch (NumberFormatException e) {
                throw new BusinessException("库存必须为整数");
            }
            if (stock < 0) throw new BusinessException("库存不能为负数");
            sku.setStock(stock);
        }
        productSkuMapper.updateById(sku);
        return Result.ok("保存成功", null);
    }

    /* ---------------- 分类 ---------------- */

    @GetMapping("/product-categories")
    public Result<List<Map<String, Object>>> categories() {
        List<ProductCategory> cats = productCategoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategory>().orderByAsc(ProductCategory::getSort));
        Map<Long, List<Long>> childIds = cats.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(ProductCategory::getParentId,
                        Collectors.mapping(ProductCategory::getId, Collectors.toList())));
        List<Map<String, Object>> list = cats.stream().map(c -> {
            // 顶级分类的商品数包含其全部子分类，与 C 端点击进入后的结果一致
            List<Long> ids = new ArrayList<>();
            ids.add(c.getId());
            ids.addAll(childIds.getOrDefault(c.getId(), List.of()));
            Long count = productMapper.selectCount(
                    new LambdaQueryWrapper<Product>().in(Product::getCategoryId, ids));
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("parentId", c.getParentId());
            m.put("icon", c.getIcon());
            m.put("sort", c.getSort());
            m.put("status", c.getStatus());
            m.put("productCount", count);
            return m;
        }).toList();
        return Result.ok(list);
    }

    @PostMapping("/product-categories")
    public Result<ProductCategory> createCategory(@RequestBody ProductCategory category) {
        if (!StringUtils.hasText(category.getName())) throw new BusinessException("分类名称不能为空");
        if (category.getStatus() == null) category.setStatus(1);
        productCategoryMapper.insert(category);
        return Result.ok("创建成功", category);
    }

    @PutMapping("/product-categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody ProductCategory body) {
        ProductCategory c = productCategoryMapper.selectById(id);
        if (c == null) throw new BusinessException("分类不存在");
        if (body.getName() != null) c.setName(body.getName());
        if (body.getParentId() != null) c.setParentId(body.getParentId());
        if (body.getSort() != null) c.setSort(body.getSort());
        if (body.getStatus() != null) c.setStatus(body.getStatus());
        productCategoryMapper.updateById(c);
        return Result.ok("更新成功", null);
    }

    @DeleteMapping("/product-categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        Long count = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getCategoryId, id));
        if (count != null && count > 0) throw new BusinessException("该分类下存在商品，无法删除");
        productCategoryMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}

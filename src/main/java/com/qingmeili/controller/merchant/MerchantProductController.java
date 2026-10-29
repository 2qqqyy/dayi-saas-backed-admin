package com.qingmeili.controller.merchant;

import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.product.ProductDTO;
import com.qingmeili.entity.Product;
import com.qingmeili.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

/**
 * 商户端 - 商品管理
 */
@RestController
@RequestMapping("/api/merchant/products")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantProductController {

    private final ProductService productService;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer onShelf,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 强制按当前登录商户隔离，防止越权看到别家商品
        return productService.pageByMerchant(UserContext.getMerchantId(),
                page, pageSize, categoryId, keyword, onShelf);
    }

    @PostMapping
    @OperationLog(module = "商品", action = "创建商品")
    public Result<Product> create(@Valid @RequestBody ProductDTO dto) {
        return productService.create(dto);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "商品", action = "编辑商品")
    public Result<Product> update(@PathVariable Long id, @Valid @RequestBody ProductDTO dto) {
        requireOwned(id);
        return productService.update(id, dto);
    }

    /** 定时上架：到点由 ProductPublishTask 自动上架 */
    @PutMapping("/{id}/schedule")
    @OperationLog(module = "商品", action = "定时上架")
    public Result<Void> schedule(@PathVariable Long id, @RequestParam String onShelfTime) {
        requireOwned(id);
        java.time.LocalDateTime time;
        try {
            time = java.time.LocalDateTime.parse(onShelfTime.trim().replace(" ", "T"));
        } catch (Exception e) {
            throw new BusinessException("时间格式应为 YYYY-MM-DD HH:mm");
        }
        if (time.isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException("定时上架时间必须晚于当前时间");
        }
        Product product = productService.getById(id);
        product.setOnShelfTime(time);
        product.setOnShelf(0);
        productService.updateById(product);
        return Result.ok("已设置定时上架", null);
    }

    @PutMapping("/{id}/status")
    @OperationLog(module = "商品", action = "更新商品上下架")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer onShelf) {
        requireOwned(id);
        return productService.updateStatus(id, onShelf);
    }

    /**
     * 列表查询靠 pageByMerchant 天然隔离，但按 id 的写操作会绕过它，
     * 必须逐个校验归属，否则任一商户可改别家商品。
     */
    private void requireOwned(Long id) {
        Product product = productService.getById(id);
        if (product == null || !Objects.equals(product.getMerchantId(), UserContext.getMerchantId())) {
            throw new BusinessException("商品不存在");
        }
    }
}

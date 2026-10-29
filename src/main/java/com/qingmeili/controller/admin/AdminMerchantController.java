package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.merchant.MerchantAuditDTO;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.Product;
import com.qingmeili.entity.Village;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.ProductMapper;
import com.qingmeili.mapper.VillageMapper;
import com.qingmeili.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台管理 - 商户管理
 */
@RestController
@RequestMapping("/api/admin/merchants")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantService merchantService;
    private final MerchantMapper merchantMapper;
    private final VillageMapper villageMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Merchant> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        if (auditStatus != null) wrapper.eq(Merchant::getAuditStatus, auditStatus);
        if (status != null) wrapper.eq(Merchant::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Merchant::getName, keyword).or().like(Merchant::getContact, keyword));
        }
        wrapper.orderByDesc(Merchant::getCreateTime);
        Page<Merchant> result = merchantMapper.selectPage(pageParam, wrapper);

        Map<Long, String> villageNames = villageMapper.selectList(null).stream()
                .collect(Collectors.toMap(Village::getId, Village::getName, (a, b) -> a));

        List<Map<String, Object>> list = result.getRecords().stream().map(m -> {
            Long productCount = productMapper.selectCount(
                    new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, m.getId()));
            Long orderCount = orderMapper.selectCount(
                    new LambdaQueryWrapper<Order>().eq(Order::getMerchantId, m.getId()));
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("name", m.getName());
            map.put("type", m.getType());
            map.put("contact", m.getContact());
            map.put("mobile", m.getMobile());
            map.put("region", m.getVillageId() != null
                    ? villageNames.getOrDefault(m.getVillageId(), "") : "");
            map.put("joinedAt", m.getCreateTime());
            map.put("productCount", productCount);
            map.put("orderCount", orderCount);
            map.put("auditStatus", m.getAuditStatus());
            map.put("status", m.getStatus());
            map.put("rejectReason", m.getRejectReason());
            map.put("rating", m.getRating());
            map.put("licenseUrl", m.getLicenseUrl());
            map.put("photoUrls", m.getPhotoUrls());
            return map;
        }).toList();

        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Merchant m = merchantMapper.selectById(id);
        if (m == null) {
            throw new BusinessException("商户不存在");
        }
        Map<Long, String> villageNames = villageMapper.selectList(null).stream()
                .collect(Collectors.toMap(Village::getId, Village::getName, (a, b) -> a));
        Map<String, Object> map = new HashMap<>();
        map.put("id", m.getId());
        map.put("name", m.getName());
        map.put("type", m.getType());
        map.put("contact", m.getContact());
        map.put("mobile", m.getMobile());
        map.put("region", m.getVillageId() != null
                ? villageNames.getOrDefault(m.getVillageId(), "") : "");
        map.put("joinedAt", m.getCreateTime());
        map.put("auditStatus", m.getAuditStatus());
        map.put("status", m.getStatus());
        // 商家自己在商家端切的「暂停接单」，后台可见且与禁用位区分开
        map.put("businessStatus", m.getBusinessStatus());
        map.put("rejectReason", m.getRejectReason());
        map.put("rating", m.getRating());
        map.put("licenseUrl", m.getLicenseUrl());
        map.put("photoUrls", m.getPhotoUrls());
        map.put("bankName", m.getBankName());
        map.put("accountName", m.getAccountName());
        map.put("accountNo", m.getAccountNo());
        map.put("address", m.getAddress());
        map.put("intro", m.getIntro());
        map.put("businessScope", m.getBusinessScope());
        return Result.ok(map);
    }

    @PostMapping("/{id}/audit")
    @OperationLog(module = "商户管理", action = "审核")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody MerchantAuditDTO dto) {
        return merchantService.audit(id, dto);
    }

    @PutMapping("/{id}/status")
    @OperationLog(module = "商户管理", action = "状态变更")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return merchantService.updateStatus(id, status);
    }
}

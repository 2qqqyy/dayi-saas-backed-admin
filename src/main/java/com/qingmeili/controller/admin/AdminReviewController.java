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
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台管理 - 评价管理
 */
@RestController
@RequestMapping("/api/admin/reviews")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final OrchardMapper orchardMapper;
    private final HomestayMapper homestayMapper;
    private final ProductMapper productMapper;
    private final MerchantMapper merchantMapper;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Review> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Review::getStatus, status);
        wrapper.orderByDesc(Review::getCreateTime);
        Page<Review> result = reviewMapper.selectPage(pageParam, wrapper);

        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : "游客", (a, b) -> a));
        Map<Long, String> orchardNames = orchardMapper.selectList(null).stream()
                .collect(Collectors.toMap(Orchard::getId, Orchard::getName, (a, b) -> a));
        Map<Long, String> homestayNames = homestayMapper.selectList(null).stream()
                .collect(Collectors.toMap(Homestay::getId, Homestay::getName, (a, b) -> a));
        Map<Long, String> productNames = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
        Map<Long, String> merchantNames = merchantMapper.selectList(null).stream()
                .collect(Collectors.toMap(Merchant::getId, Merchant::getName, (a, b) -> a));

        List<Map<String, Object>> list = result.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("user", userNames.getOrDefault(r.getUserId(), "游客"));
            m.put("target", resolveTarget(r, orchardNames, homestayNames, productNames, merchantNames));
            m.put("rating", r.getScore());
            m.put("content", r.getContent());
            m.put("images", r.getImages());
            m.put("reply", r.getReply());
            m.put("status", r.getStatus());
            m.put("time", r.getCreateTime());
            return m;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** 显示/隐藏：status 1显示 0隐藏 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Review r = reviewMapper.selectById(id);
        if (r == null) throw new BusinessException("评价不存在");
        r.setStatus(status);
        reviewMapper.updateById(r);
        return Result.ok("状态更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Review r = reviewMapper.selectById(id);
        if (r == null) throw new BusinessException("评价不存在");
        reviewMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }

    private String resolveTarget(Review r, Map<Long, String> orchardNames, Map<Long, String> homestayNames,
                                 Map<Long, String> productNames, Map<Long, String> merchantNames) {
        // 评价目标：type 1采摘 2民宿 3商品，目标按商户名兜底
        String merchant = merchantNames.getOrDefault(r.getMerchantId(), "");
        return switch (r.getType() != null ? r.getType() : 0) {
            case 1 -> orchardNames.getOrDefault(r.getMerchantId(), merchant);
            case 2 -> homestayNames.getOrDefault(r.getMerchantId(), merchant);
            case 3 -> productNames.getOrDefault(r.getMerchantId(), merchant);
            default -> merchant;
        };
    }
}

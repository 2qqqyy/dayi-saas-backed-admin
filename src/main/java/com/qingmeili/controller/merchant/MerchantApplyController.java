package com.qingmeili.controller.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.merchant.MerchantApplyDTO;
import com.qingmeili.entity.Merchant;
import com.qingmeili.mapper.MerchantMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商户端 - 入驻申请 & 商户资料
 */
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantApplyController {

    private final MerchantMapper merchantMapper;

    @PostMapping("/apply")
    @OperationLog(module = "商户入驻", action = "提交申请")
    public Result<Merchant> apply(@Valid @RequestBody MerchantApplyDTO dto) {
        // 检查是否已有商户记录
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Merchant::getUserId, UserContext.getUserId());
        Merchant existing = merchantMapper.selectOne(wrapper);
        if (existing != null) {
            return Result.fail("您已提交过申请");
        }

        Merchant merchant = new Merchant();
        merchant.setUserId(UserContext.getUserId());
        merchant.setName(dto.getName());
        merchant.setType(dto.getType());
        merchant.setContact(dto.getContact());
        merchant.setMobile(dto.getMobile());
        merchant.setVillageId(dto.getVillageId());
        merchant.setIdCard(dto.getIdCard());
        merchant.setLicenseUrl(dto.getLicenseUrl());
        merchant.setPhotoUrls(dto.getPhotoUrls());
        merchant.setBankName(dto.getBankName());
        merchant.setAccountName(dto.getAccountName());
        merchant.setAccountNo(dto.getAccountNo());
        merchant.setAuditStatus(0);
        merchant.setRejectCount(0);
        merchant.setStatus(0);
        merchantMapper.insert(merchant);
        return Result.ok("申请提交成功", merchant);
    }

    /**
     * 商家端自助入驻（未登录可用）
     * POST /api/merchant/apply/public
     *
     * 商家注册页在登录前提交，因此不能依赖 UserContext。
     * 写入待审核商户记录，平台审核通过后该手机号即可登录商家端。
     */
    @PostMapping("/apply/public")
    @OperationLog(module = "商户入驻", action = "自助申请")
    public Result<Void> publicApply(@Valid @RequestBody MerchantApplyDTO dto) {
        if (dto.getMobile() == null || !dto.getMobile().matches("^1[3-9]\\d{9}$")) {
            return Result.fail("手机号格式不正确");
        }
        Long exists = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getMobile, dto.getMobile()));
        if (exists != null && exists > 0) {
            return Result.fail("该手机号已提交过入驻申请");
        }

        Merchant merchant = new Merchant();
        merchant.setName(dto.getName());
        merchant.setType(dto.getType());
        merchant.setContact(dto.getContact());
        merchant.setMobile(dto.getMobile());
        merchant.setVillageId(dto.getVillageId() != null ? dto.getVillageId() : 1L);
        merchant.setAddress(dto.getAddress());
        merchant.setIntro(dto.getIntro());
        merchant.setBusinessScope(dto.getBusinessScope());
        merchant.setAuditStatus(0);
        merchant.setRejectCount(0);
        merchant.setStatus(0);
        merchant.setRating(new java.math.BigDecimal("5.00"));
        merchantMapper.insert(merchant);
        return Result.ok("申请已提交，请等待平台审核", null);
    }

    @GetMapping("/apply/status")
    @RequireRole({"tourist", "user", "merchant"})
    public Result<Merchant> applyStatus() {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Merchant::getUserId, UserContext.getUserId());
        Merchant merchant = merchantMapper.selectOne(wrapper);
        if (merchant == null) {
            return Result.fail("暂无申请记录");
        }
        return Result.ok(merchant);
    }

    @GetMapping("/profile")
    @RequireRole({"merchant"})
    public Result<Merchant> getProfile() {
        Merchant merchant = getMerchantByContext();
        if (merchant == null) {
            return Result.fail("商户不存在");
        }
        return Result.ok(merchant);
    }

    @PutMapping("/profile")
    @RequireRole({"merchant"})
    @OperationLog(module = "商户资料", action = "更新资料")
    public Result<Void> updateProfile(@Valid @RequestBody MerchantApplyDTO dto) {
        Merchant merchant = getMerchantByContext();
        if (merchant == null) {
            return Result.fail("商户不存在");
        }

        // 只更新请求里带了（非 null）的字段：商家端资料页只编辑其中几项，
        // 全量覆盖会把未提交的营业执照、照片、结算账户等清空。
        if (dto.getName() != null) merchant.setName(dto.getName());
        if (dto.getType() != null) merchant.setType(dto.getType());
        if (dto.getContact() != null) merchant.setContact(dto.getContact());
        if (dto.getMobile() != null && !dto.getMobile().contains("*")) merchant.setMobile(dto.getMobile());
        if (dto.getVillageId() != null) merchant.setVillageId(dto.getVillageId());
        if (dto.getIdCard() != null) merchant.setIdCard(dto.getIdCard());
        if (dto.getLicenseUrl() != null) merchant.setLicenseUrl(dto.getLicenseUrl());
        if (dto.getPhotoUrls() != null) merchant.setPhotoUrls(dto.getPhotoUrls());
        if (dto.getBankName() != null) merchant.setBankName(dto.getBankName());
        if (dto.getAccountName() != null) merchant.setAccountName(dto.getAccountName());
        if (dto.getAccountNo() != null) merchant.setAccountNo(dto.getAccountNo());
        if (dto.getAddress() != null) merchant.setAddress(dto.getAddress());
        if (dto.getIntro() != null) merchant.setIntro(dto.getIntro());
        if (dto.getBusinessScope() != null) merchant.setBusinessScope(dto.getBusinessScope());
        if (dto.getOpenTime() != null) merchant.setOpenTime(dto.getOpenTime());
        if (dto.getShopNotice() != null) merchant.setShopNotice(dto.getShopNotice());
        merchantMapper.updateById(merchant);
        return Result.ok("更新成功", null);
    }

    @PutMapping("/avatar")
    @RequireRole({"merchant"})
    @OperationLog(module = "商户资料", action = "更换头像")
    public Result<Void> updateAvatar(@RequestBody Map<String, String> body) {
        Merchant merchant = getMerchantByContext();
        if (merchant == null) {
            return Result.fail("商户不存在");
        }
        String avatar = body == null ? null : body.get("avatar");
        if (avatar == null || avatar.isBlank()) {
            return Result.fail("头像地址不能为空");
        }
        merchant.setAvatar(avatar.trim());
        merchantMapper.updateById(merchant);
        return Result.ok("更新成功", null);
    }

    private Merchant getMerchantByContext() {
        Long merchantId = UserContext.getMerchantId();
        if (merchantId == null) {
            // 尝试按userId查找
            LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Merchant::getUserId, UserContext.getUserId());
            return merchantMapper.selectOne(wrapper);
        }
        return merchantMapper.selectById(merchantId);
    }
}

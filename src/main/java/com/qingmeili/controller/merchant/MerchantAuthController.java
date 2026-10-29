package com.qingmeili.controller.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.common.util.JwtUtil;
import com.qingmeili.common.util.PasswordUtil;
import com.qingmeili.dto.merchant.MerchantLoginDTO;
import com.qingmeili.dto.merchant.MerchantLoginResponse;
import com.qingmeili.entity.Merchant;
import com.qingmeili.mapper.MerchantMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商户端 - 登录认证
 *
 * 说明：/api/merchant/** 默认在 JwtInterceptor 拦截范围内，
 * 但登录接口本身必须放行（已在 WebMvcConfig 白名单中排除）。
 */
@Slf4j
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantAuthController {
    private final com.qingmeili.service.SmsSender smsSender;

    @org.springframework.beans.factory.annotation.Value("${sms.demo-code:}")
    private String smsDemoCode;


    private final MerchantMapper merchantMapper;
    private final JwtUtil jwtUtil;

    /** 演示环境固定验证码（application.yml sms.demo-code） */
    @Value("${sms.demo-code:}")
    private String demoSmsCode;

    /**
     * 商户登录
     * POST /api/merchant/login  { mobile, code, method }
     */
    @PostMapping("/login")
    public Result<MerchantLoginResponse> login(@Valid @RequestBody MerchantLoginDTO dto) {
        // 1. 按手机号查商户
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getMobile, dto.getMobile()));
        if (merchant == null) {
            throw new BusinessException("账号不存在，请先提交入驻申请");
        }

        // 2. 校验凭证
        String method = dto.getMethod() == null ? "password" : dto.getMethod();
        if ("sms".equalsIgnoreCase(method)) {
            // 演示环境固定验证码 123456（与 C 端保持一致）
            boolean ok = "123456".equals(dto.getCode())
                    || (demoSmsCode != null && !demoSmsCode.isBlank() && demoSmsCode.equals(dto.getCode()));
            if (!ok) {
                throw new BusinessException("验证码错误");
            }
        } else {
            // 密码登录：优先校验 merchant.password_hash（商户改密后写入），
            // 为空时回退「演示初始密码 123456」策略
            boolean ok = merchant.getPasswordHash() != null && !merchant.getPasswordHash().isBlank()
                    ? PasswordUtil.matches(dto.getCode(), merchant.getPasswordHash())
                    : "123456".equals(dto.getCode());
            if (!ok) {
                throw new BusinessException("账号或密码错误");
            }
        }

        // 3. 状态校验：0=禁用/暂停营业。
        // 允许登录但只读：商家需要能看到被处罚原因并申诉；下单侧已按商家状态拦截。
        if (merchant.getStatus() != null && merchant.getStatus() == 0) {
            log.info("【商户登录】merchantId={} 处于暂停营业/禁用状态，仅可查看", merchant.getId());
        }

        // 4. 签发 merchant token
        Long userId = merchant.getUserId() != null ? merchant.getUserId() : merchant.getId();
        String token = jwtUtil.generateMerchantToken(userId, merchant.getId());

        log.info("【商户登录】merchantId={}, name={}, mobile={}", merchant.getId(), merchant.getName(), merchant.getMobile());
        return Result.ok("登录成功",
                new MerchantLoginResponse(token, System.currentTimeMillis() + 86400000L, merchant));
    }

    /**
     * 登出（JWT 无状态，仅清理上下文）
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        UserContext.clear();
        return Result.ok("退出成功", null);
    }

    /**
     * 发送登录验证码（商户端登录页）
     * POST /api/merchant/auth/sms/code  { mobile }
     *
     * 演示环境下验证码固定为 123456，此处仅做手机号格式校验并返回有效期，
     * 与 C 端 /api/auth/sms/code 行为保持一致；真正的登录校验在 /login 中。
     */
    @PostMapping("/auth/sms/code")
    public Result<Map<String, Object>> sendSmsCode(@RequestBody Map<String, String> body) {
        String mobile = body == null ? null : body.get("mobile");
        if (mobile == null || !mobile.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        String demo = smsDemoCode != null && !smsDemoCode.isBlank() ? smsDemoCode : "123456";
        smsSender.sendCode(mobile, demo);
        return Result.ok("请求成功", Map.of("expire", 60));
    }

    /**
     * 当前登录商户信息（等价于 /profile，供前端校验 token 有效性）
     */
    @GetMapping("/me")
    @com.qingmeili.common.annotation.RequireRole({"merchant"})
    public Result<Merchant> me() {
        Long merchantId = UserContext.getMerchantId();
        Merchant merchant = merchantId != null
                ? merchantMapper.selectById(merchantId)
                : merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getUserId, UserContext.getUserId()));
        if (merchant == null) {
            return Result.fail("商户不存在");
        }
        return Result.ok(merchant);
    }

    /**
     * 商户营业状态切换
     * PUT /api/merchant/status  { status: 0|1 }  0=暂停接单 1=营业中
     *
     * 只写 business_status：merchant.status 是后台「禁用商家」位，登录接口按它拦截，
     * 若在这里覆盖成 0，商家自己点一下「暂停营业」就会再也登不上商家端。
     */
    @PutMapping("/status")
    @com.qingmeili.common.annotation.RequireRole({"merchant"})
    public Result<Void> updateStatus(@RequestBody Map<String, Object> body) {
        Object status = body != null ? body.get("status") : null;
        if (status == null) {
            throw new BusinessException("状态不能为空");
        }
        int value;
        try {
            value = Integer.parseInt(String.valueOf(status));
        } catch (NumberFormatException e) {
            throw new BusinessException("状态值不合法");
        }
        if (value != 0 && value != 1) {
            throw new BusinessException("状态值不合法");
        }
        Long merchantId = UserContext.getMerchantId();
        if (merchantId == null || merchantMapper.selectById(merchantId) == null) {
            throw new BusinessException("商户不存在");
        }
        merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, merchantId)
                .set(Merchant::getBusinessStatus, value));
        log.info("【商户营业状态】merchantId={}, businessStatus={}", merchantId, value);
        return Result.ok("状态更新成功", null);
    }

    /**
     * 商户修改登录密码
     * POST /api/merchant/password  { oldPassword, newPassword }
     *
     * 首次修改前商户无 password_hash，原密码按演示初始密码 123456 校验；
     * 修改后写入 BCrypt 哈希，登录时优先按哈希校验。
     */
    @PostMapping("/password")
    @com.qingmeili.common.annotation.RequireRole({"merchant"})
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body != null ? body.get("oldPassword") : null;
        String newPassword = body != null ? body.get("newPassword") : null;
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException("原密码不能为空");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码长度不能少于6位");
        }
        Merchant merchant = merchantMapper.selectById(UserContext.getMerchantId());
        if (merchant == null) {
            throw new BusinessException("商户不存在");
        }
        boolean oldOk = merchant.getPasswordHash() != null && !merchant.getPasswordHash().isBlank()
                ? PasswordUtil.matches(oldPassword, merchant.getPasswordHash())
                : "123456".equals(oldPassword);
        if (!oldOk) {
            throw new BusinessException("原密码错误");
        }
        merchant.setPasswordHash(PasswordUtil.encode(newPassword));
        merchantMapper.updateById(merchant);
        log.info("【商户改密】merchantId={}", merchant.getId());
        return Result.ok("密码修改成功", null);
    }
}

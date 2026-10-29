package com.qingmeili.controller;

import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.common.util.CaptchaStore;
import com.qingmeili.dto.auth.AdminLoginDTO;
import com.qingmeili.dto.auth.AdminLoginResponse;
import com.qingmeili.entity.Admin;
import com.qingmeili.service.AdminService;
import com.qingmeili.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员认证控制器
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;
    private final AdminService adminService;
    private final CaptchaStore captchaStore;

    /**
     * 获取图形验证码：答案只存服务端，登录时校验
     */
    @GetMapping("/captcha")
    public Result<CaptchaStore.Captcha> captcha() {
        return Result.ok(captchaStore.generate());
    }

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginDTO dto) {
        return Result.ok("登录成功", authService.adminLogin(dto));
    }

    /**
     * 获取当前管理员信息
     */
    @GetMapping("/info")
    @RequireRole({"admin", "super_admin"})
    public Result<Admin> info() {
        return adminService.getAdminInfo(UserContext.getUserId());
    }
}

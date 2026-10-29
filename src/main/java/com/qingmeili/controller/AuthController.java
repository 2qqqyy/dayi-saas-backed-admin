package com.qingmeili.controller;

import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.*;
import com.qingmeili.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C端认证控制器（公开接口）
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 发送短信验证码
     */
    @PostMapping("/sms/code")
    public Result<SmsResponse> sendSmsCode(@Valid @RequestBody SmsCodeDTO dto) {
        return Result.ok(authService.sendSmsCode(dto));
    }

    /**
     * 手机号验证码登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.ok("登录成功", authService.userLogin(dto));
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody UserRegisterDTO dto) {
        return Result.ok("注册成功", authService.userRegister(dto));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok("退出成功", null);
    }
}

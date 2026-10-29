package com.qingmeili.controller.api;

import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.LoginResponse;
import com.qingmeili.dto.auth.SmsCodeDTO;
import com.qingmeili.dto.auth.SmsResponse;
import com.qingmeili.dto.auth.UserLoginDTO;
import com.qingmeili.dto.user.UserUpdateDTO;
import com.qingmeili.entity.User;
import com.qingmeili.mapper.UserMapper;
import com.qingmeili.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C端 - 用户信息
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final AuthService authService;

    /** H5 登录链路别名：POST /api/user/sms */
    @PostMapping("/sms")
    public Result<SmsResponse> sendSms(@Valid @RequestBody SmsCodeDTO dto) {
        return Result.ok(authService.sendSmsCode(dto));
    }

    /** H5 登录链路别名：POST /api/user/login */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.ok("登录成功", authService.userLogin(dto));
    }

    @GetMapping("/info")
    public Result<User> getInfo() {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    @PutMapping("/info")
    public Result<Void> updateInfo(@Valid @RequestBody UserUpdateDTO dto) {
        User user = new User();
        user.setId(UserContext.getUserId());
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        userMapper.updateById(user);
        return Result.ok("更新成功", null);
    }
}

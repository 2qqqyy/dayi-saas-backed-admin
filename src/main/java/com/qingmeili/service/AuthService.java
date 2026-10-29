package com.qingmeili.service;

import com.qingmeili.dto.auth.*;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 发送短信验证码
     */
    SmsResponse sendSmsCode(SmsCodeDTO dto);

    /**
     * C端手机号验证码登录
     */
    LoginResponse userLogin(UserLoginDTO dto);

    /**
     * C端注册
     */
    LoginResponse userRegister(UserRegisterDTO dto);

    /**
     * 管理员账号密码登录
     */
    AdminLoginResponse adminLogin(AdminLoginDTO dto);

    /**
     * 退出登录
     */
    void logout();
}

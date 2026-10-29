package com.qingmeili.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户端登录请求
 * 支持两种方式：password（账号密码）、sms（手机号验证码）
 */
@Data
public class MerchantLoginDTO {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String mobile;

    /** 密码或验证码（根据 method 决定） */
    @NotBlank(message = "密码/验证码不能为空")
    private String code;

    /** 登录方式：password | sms，默认 password */
    private String method = "password";
}

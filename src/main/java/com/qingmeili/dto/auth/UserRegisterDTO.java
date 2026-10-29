package com.qingmeili.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * C端注册请求
 */
@Data
public class UserRegisterDTO {

    @NotBlank(message = "手机号不能为空")
    private String mobile;

    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 留空则由服务端按手机号生成默认昵称 */
    @Size(max = 20, message = "昵称最长 20 个字符")
    private String nickname;

    private Boolean agreed;
}

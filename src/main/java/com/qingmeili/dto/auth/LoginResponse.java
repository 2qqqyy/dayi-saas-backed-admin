package com.qingmeili.dto.auth;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录成功响应
 */
@Data
public class LoginResponse {
    private String token;
    private Long expireAt;
    private UserInfo userInfo;

    @Data
    public static class UserInfo {
        private Long id;
        private String mobile;
        private String nickname;
        private String avatar;
        private String level;
    }
}

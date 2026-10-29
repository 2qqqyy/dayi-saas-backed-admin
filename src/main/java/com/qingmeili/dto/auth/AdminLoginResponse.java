package com.qingmeili.dto.auth;

import lombok.Data;

/**
 * 管理员登录响应
 */
@Data
public class AdminLoginResponse {
    private String token;
    private Long expireAt;
    private AdminInfo adminInfo;

    @Data
    public static class AdminInfo {
        private Long id;
        private String username;
        private String realName;
        private String avatar;
        private String role;
    }
}

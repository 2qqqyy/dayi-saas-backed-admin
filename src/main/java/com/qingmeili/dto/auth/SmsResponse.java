package com.qingmeili.dto.auth;

import lombok.Data;

/**
 * 短信发送响应
 */
@Data
public class SmsResponse {
    private Integer expire;

    public SmsResponse(Integer expire) {
        this.expire = expire;
    }
}

package com.qingmeili.dto.merchant;

import com.qingmeili.entity.Merchant;
import lombok.Data;

/**
 * 商户端登录响应
 */
@Data
public class MerchantLoginResponse {

    private String token;

    private Long expireAt;

    private Long merchantId;

    /** 商户资料（兼容商家端 H5 merchantInfo 字段） */
    private Merchant merchantInfo;

    public MerchantLoginResponse() {}

    public MerchantLoginResponse(String token, Long expireAt, Merchant merchantInfo) {
        this.token = token;
        this.expireAt = expireAt;
        this.merchantInfo = merchantInfo;
        this.merchantId = merchantInfo != null ? merchantInfo.getId() : null;
    }
}

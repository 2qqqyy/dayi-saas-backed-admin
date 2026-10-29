package com.qingmeili.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商户入驻申请DTO
 */
@Data
public class MerchantApplyDTO {

    @NotBlank(message = "商户名称不能为空")
    private String name;

    @NotNull(message = "商户类型不能为空")
    private Integer type;

    @NotBlank(message = "联系人不能为空")
    private String contact;

    @NotBlank(message = "联系电话不能为空")
    private String mobile;

    private Long villageId;

    private String idCard;

    private String licenseUrl;

    private String photoUrls;

    private String bankName;

    private String accountName;

    private String accountNo;

    /** 以下三项为商家可自助维护的资料字段，后台商家详情与 C 端商家页共用 */
    private String address;

    private String intro;

    private String businessScope;

    private String openTime;

    private String shopNotice;
}

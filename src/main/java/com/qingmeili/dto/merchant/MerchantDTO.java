package com.qingmeili.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 商户DTO - 创建/更新
 */
@Data
public class MerchantDTO {

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
}

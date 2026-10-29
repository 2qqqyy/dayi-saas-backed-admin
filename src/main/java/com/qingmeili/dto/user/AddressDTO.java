package com.qingmeili.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 收货地址DTO
 */
@Data
public class AddressDTO {

    @NotBlank(message = "收货人不能为空")
    private String receiver;

    @NotBlank(message = "手机号不能为空")
    private String mobile;

    private String province;

    private String city;

    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    private Integer isDefault;
}

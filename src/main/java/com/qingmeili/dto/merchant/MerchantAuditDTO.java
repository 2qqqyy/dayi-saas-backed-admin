package com.qingmeili.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户审核DTO
 */
@Data
public class MerchantAuditDTO {

    /**
     * 操作：approve / reject
     */
    @NotBlank(message = "操作类型不能为空")
    private String action;

    /**
     * 驳回原因（驳回时必填）
     */
    private String reason;
}

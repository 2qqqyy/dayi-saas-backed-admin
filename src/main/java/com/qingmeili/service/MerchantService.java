package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.merchant.MerchantAuditDTO;
import com.qingmeili.entity.Merchant;

/**
 * 商户服务
 */
public interface MerchantService extends IService<Merchant> {

    Result<PageResult<Merchant>> page(Integer page, Integer pageSize, Integer auditStatus, String keyword);

    Result<Merchant> getDetail(Long id);

    Result<Void> audit(Long id, MerchantAuditDTO dto);

    Result<Void> updateStatus(Long id, Integer status);
}

package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Settlement;
import com.qingmeili.entity.Withdrawal;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 结算/提现服务
 */
public interface SettlementService extends IService<Settlement> {

    Result<PageResult<Settlement>> page(Long merchantId, Integer page, Integer pageSize);

    Result<Withdrawal> applyWithdrawal(Long merchantId, BigDecimal amount);

    Result<PageResult<Withdrawal>> listWithdrawals(Long merchantId, Integer page, Integer pageSize);

    Result<Map<String, Object>> statisticsOverview(Long merchantId);
}

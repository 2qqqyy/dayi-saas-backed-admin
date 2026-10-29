package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.Settlement;
import com.qingmeili.entity.Withdrawal;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.SettlementMapper;
import com.qingmeili.mapper.WithdrawalMapper;
import com.qingmeili.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 结算/提现服务实现
 */
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl extends ServiceImpl<SettlementMapper, Settlement> implements SettlementService {

    private final WithdrawalMapper withdrawalMapper;
    private final MerchantMapper merchantMapper;

    @Override
    public Result<PageResult<Settlement>> page(Long merchantId, Integer page, Integer pageSize) {
        Page<Settlement> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Settlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Settlement::getMerchantId, merchantId);
        wrapper.orderByDesc(Settlement::getCreateTime);
        Page<Settlement> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    @Transactional
    public Result<Withdrawal> applyWithdrawal(Long merchantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("提现金额必须大于0");
        }

        // 可提现余额 = 待结算金额 - 处理中/已到账提现
        BigDecimal available = available(merchantId);
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("没有可提现的结算金额");
        }
        if (amount.compareTo(available) > 0) {
            throw new BusinessException("可提现余额不足，当前最多可提 ¥" + available.setScale(2, java.math.RoundingMode.HALF_UP));
        }

        // 获取商户银行账户信息
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException("商户不存在");
        }

        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setMerchantId(merchantId);
        withdrawal.setAmount(amount);
        withdrawal.setBankName(merchant.getBankName() != null ? merchant.getBankName() : "");
        withdrawal.setAccountName(merchant.getAccountName() != null ? merchant.getAccountName() : "");
        withdrawal.setAccountNo(merchant.getAccountNo() != null ? merchant.getAccountNo() : "");
        withdrawal.setStatus(0);
        withdrawalMapper.insert(withdrawal);

        return Result.ok("提现申请已提交", withdrawal);
    }

    @Override
    public Result<PageResult<Withdrawal>> listWithdrawals(Long merchantId, Integer page, Integer pageSize) {
        Page<Withdrawal> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Withdrawal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Withdrawal::getMerchantId, merchantId);
        wrapper.orderByDesc(Withdrawal::getCreateTime);
        Page<Withdrawal> result = withdrawalMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<Map<String, Object>> statisticsOverview(Long merchantId) {
        Map<String, Object> data = new HashMap<>();

        // 待提现金额
        data.put("pendingAmount", available(merchantId));

        // 已提现金额
        var withdrawnWrapper = new LambdaQueryWrapper<Withdrawal>()
                .eq(Withdrawal::getMerchantId, merchantId)
                .eq(Withdrawal::getStatus, 1);
        var withdrawals = withdrawalMapper.selectList(withdrawnWrapper);
        BigDecimal withdrawnAmount = withdrawals.stream()
                .map(Withdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("withdrawnAmount", withdrawnAmount);

        // 总销售额
        var totalWrapper = new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getMerchantId, merchantId);
        var allSettlements = baseMapper.selectList(totalWrapper);
        BigDecimal totalAmount = allSettlements.stream()
                .map(s -> s.getOrderAmount() != null ? s.getOrderAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("totalSales", totalAmount);

        return Result.ok(data);
    }

    /** 可用余额：待结算合计 - 处理中/已到账提现，最低为 0 */
    public BigDecimal available(Long merchantId) {
        java.util.List<Settlement> pending = baseMapper.selectList(new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getMerchantId, merchantId)
                .eq(Settlement::getStatus, 0));
        BigDecimal pendingSum = pending.stream()
                .map(x -> x.getSettleAmount() == null ? BigDecimal.ZERO : x.getSettleAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        java.util.List<Withdrawal> withdrawals = withdrawalMapper.selectList(
                new LambdaQueryWrapper<Withdrawal>()
                        .eq(Withdrawal::getMerchantId, merchantId)
                        .in(Withdrawal::getStatus, 0, 1));
        BigDecimal taken = withdrawals.stream()
                .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal left = pendingSum.subtract(taken);
        return left.signum() < 0 ? BigDecimal.ZERO : left;
    }
}

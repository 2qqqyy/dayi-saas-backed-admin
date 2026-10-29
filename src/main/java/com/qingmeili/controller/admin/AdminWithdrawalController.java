package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Settlement;
import com.qingmeili.entity.Withdrawal;
import com.qingmeili.mapper.SettlementMapper;
import com.qingmeili.mapper.WithdrawalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 平台 - 商家提现审核。此前提现单提交后无人可审，状态永远停在"处理中"。
 */
@RestController
@RequestMapping("/api/admin/withdrawals")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminWithdrawalController {

    private final WithdrawalMapper withdrawalMapper;
    private final SettlementMapper settlementMapper;

    @GetMapping
    public Result<PageResult<Withdrawal>> list(@RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Withdrawal> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Withdrawal> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Withdrawal::getStatus, status);
        }
        wrapper.orderByDesc(Withdrawal::getCreateTime);
        Page<Withdrawal> result = withdrawalMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @PutMapping("/{id}/audit")
    @org.springframework.transaction.annotation.Transactional
    @OperationLog(module = "提现审核", action = "审核提现单")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Withdrawal w = withdrawalMapper.selectById(id);
        if (w == null) {
            throw new BusinessException("提现单不存在");
        }
        if (w.getStatus() != null && w.getStatus() != 0) {
            throw new BusinessException("该提现单已审核");
        }
        Object statusObj = body == null ? null : body.get("status");
        int next = statusObj == null ? -1 : Integer.parseInt(String.valueOf(statusObj));
        if (next != 1 && next != 2) {
            throw new BusinessException("审核结果只能是通过(1)或驳回(2)");
        }
        w.setStatus(next);
        if (next == 1) {
            consumeSettlements(w.getMerchantId(), w.getAmount());
            w.setArrivalDate(LocalDate.now());
        } else {
            Object reason = body.get("reason");
            w.setRemark(reason == null ? "审核驳回" : String.valueOf(reason));
        }
        withdrawalMapper.updateById(w);
        return Result.ok(next == 1 ? "已通过并标记到账" : "已驳回", null);
    }

    /**
     * 提现通过时按时间顺序核销待结算记录：能覆盖的整条置为已结算，
     * 差一点的那条扣减剩余金额，避免同一笔钱被重复申请提现。
     */
    private void consumeSettlements(Long merchantId, java.math.BigDecimal amount) {
        java.math.BigDecimal remain = amount == null ? java.math.BigDecimal.ZERO : amount;
        java.util.List<Settlement> list = settlementMapper.selectList(
                new LambdaQueryWrapper<Settlement>()
                        .eq(Settlement::getMerchantId, merchantId)
                        .eq(Settlement::getStatus, 0)
                        .orderByAsc(Settlement::getCreateTime));
        for (Settlement settle : list) {
            if (remain.signum() <= 0) {
                break;
            }
            java.math.BigDecimal value = settle.getSettleAmount() == null
                    ? java.math.BigDecimal.ZERO : settle.getSettleAmount();
            if (value.signum() <= 0) {
                settle.setStatus(1);
                settlementMapper.updateById(settle);
                continue;
            }
            if (value.compareTo(remain) <= 0) {
                settle.setStatus(1);
                remain = remain.subtract(value);
            } else {
                settle.setSettleAmount(value.subtract(remain));
                remain = java.math.BigDecimal.ZERO;
            }
            settlementMapper.updateById(settle);
        }
        if (remain.signum() > 0) {
            throw new BusinessException("可结算余额不足，无法通过该提现");
        }
    }
}

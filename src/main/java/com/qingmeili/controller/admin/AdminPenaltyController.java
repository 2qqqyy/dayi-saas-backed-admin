package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.MerchantPenalty;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.MerchantPenaltyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台 - 商家处罚：警告 / 扣分 / 暂停营业（到期由任务自动恢复）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminPenaltyController {

    private final MerchantPenaltyMapper penaltyMapper;
    private final MerchantMapper merchantMapper;

    @GetMapping("/penalties")
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Long merchantId,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<MerchantPenalty> param = new Page<>(page, pageSize);
        LambdaQueryWrapper<MerchantPenalty> w = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            w.eq(MerchantPenalty::getMerchantId, merchantId);
        }
        if (status != null) {
            w.eq(MerchantPenalty::getStatus, status);
        }
        w.orderByDesc(MerchantPenalty::getCreateTime);
        Page<MerchantPenalty> result = penaltyMapper.selectPage(param, w);
        java.util.List<Map<String, Object>> rows = result.getRecords().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("penalty", p);
            Merchant merchant = merchantMapper.selectById(p.getMerchantId());
            m.put("merchantName", merchant == null ? "" : merchant.getName());
            return m;
        }).toList();
        return Result.ok(PageResult.of(rows, result.getTotal(), page, pageSize));
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/merchants/{merchantId}/penalties")
    @OperationLog(module = "商家处罚", action = "新增处罚")
    public Result<MerchantPenalty> issue(@PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException("商家不存在");
        }
        String type = str(body.get("type"), "WARNING").toUpperCase();
        if (!java.util.Set.of("WARNING", "DEDUCT", "SUSPEND").contains(type)) {
            throw new BusinessException("处罚类型不合法");
        }
        String reason = str(body.get("reason"), "").trim();
        if (reason.isEmpty()) {
            throw new BusinessException("请填写处罚原因");
        }
        int points = body.get("points") == null ? 0 : Integer.parseInt(String.valueOf(body.get("points")));
        int days = body.get("suspendDays") == null ? 0 : Integer.parseInt(String.valueOf(body.get("suspendDays")));
        if ("DEDUCT".equals(type) && points <= 0) {
            throw new BusinessException("扣分必须大于 0");
        }
        if ("SUSPEND".equals(type) && days <= 0) {
            throw new BusinessException("暂停营业需填写天数");
        }

        MerchantPenalty p = new MerchantPenalty();
        p.setMerchantId(merchantId);
        p.setType(type);
        p.setPoints("DEDUCT".equals(type) ? points : 0);
        p.setReason(reason);
        p.setSource(str(body.get("source"), "巡检"));
        p.setStatus(1);
        p.setStartTime(LocalDateTime.now());
        p.setOperator(str(body.get("operator"), "平台"));
        if ("SUSPEND".equals(type)) {
            p.setEndTime(LocalDateTime.now().plusDays(days));
            // 只暂停营业，不动平台禁用位 status，避免撤销处罚时误恢复被平台封禁的商家
            merchant.setBusinessStatus(0);
            merchantMapper.updateById(merchant);
        }
        penaltyMapper.insert(p);
        return Result.ok("处罚已生效", p);
    }

    @PutMapping("/penalties/{id}/revoke")
    @OperationLog(module = "商家处罚", action = "撤销处罚")
    public Result<Void> revoke(@PathVariable Long id) {
        MerchantPenalty p = penaltyMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("处罚记录不存在");
        }
        if (p.getStatus() != null && p.getStatus() == 0) {
            throw new BusinessException("该处罚已撤销");
        }
        p.setStatus(0);
        penaltyMapper.updateById(p);
        if ("SUSPEND".equals(p.getType())) {
            restoreIfNoActiveSuspend(p.getMerchantId());
        }
        return Result.ok("已撤销", null);
    }

    /** 扣分与处罚汇总：商家详情页/列表展示 */
    @GetMapping("/merchants/{merchantId}/penalty-summary")
    public Result<Map<String, Object>> summary(@PathVariable Long merchantId) {
        java.util.List<MerchantPenalty> list = penaltyMapper.selectList(new LambdaQueryWrapper<MerchantPenalty>()
                .eq(MerchantPenalty::getMerchantId, merchantId)
                .eq(MerchantPenalty::getStatus, 1));
        int points = list.stream().filter(x -> "DEDUCT".equals(x.getType()))
                .mapToInt(x -> x.getPoints() == null ? 0 : x.getPoints()).sum();
        boolean suspended = list.stream().anyMatch(x -> "SUSPEND".equals(x.getType())
                && x.getEndTime() != null && x.getEndTime().isAfter(LocalDateTime.now()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", points);
        data.put("suspended", suspended);
        data.put("records", list.size());
        return Result.ok(data);
    }

    private void restoreIfNoActiveSuspend(Long merchantId) {
        Long active = penaltyMapper.selectCount(new LambdaQueryWrapper<MerchantPenalty>()
                .eq(MerchantPenalty::getMerchantId, merchantId)
                .eq(MerchantPenalty::getType, "SUSPEND")
                .eq(MerchantPenalty::getStatus, 1)
                .gt(MerchantPenalty::getEndTime, LocalDateTime.now()));
        if (active == null || active == 0) {
            Merchant m = merchantMapper.selectById(merchantId);
            if (m != null) {
                m.setBusinessStatus(1);
                merchantMapper.updateById(m);
            }
        }
    }

    private String str(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }
}

package com.qingmeili.controller.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.MerchantPenalty;
import com.qingmeili.mapper.MerchantPenaltyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家端 - 我的违规与处罚
 */
@RestController
@RequestMapping("/api/merchant/penalties")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantPenaltyController {

    private final MerchantPenaltyMapper penaltyMapper;

    @GetMapping
    public Result<Map<String, Object>> list() {
        Long merchantId = UserContext.getMerchantId();
        List<MerchantPenalty> list = penaltyMapper.selectList(new LambdaQueryWrapper<MerchantPenalty>()
                .eq(MerchantPenalty::getMerchantId, merchantId)
                .orderByDesc(MerchantPenalty::getCreateTime));
        int points = list.stream()
                .filter(x -> "DEDUCT".equals(x.getType()) && x.getStatus() != null && x.getStatus() == 1)
                .mapToInt(x -> x.getPoints() == null ? 0 : x.getPoints()).sum();
        boolean suspended = list.stream().anyMatch(x -> "SUSPEND".equals(x.getType())
                && x.getStatus() != null && x.getStatus() == 1
                && x.getEndTime() != null && x.getEndTime().isAfter(LocalDateTime.now()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", points);
        data.put("suspended", suspended);
        data.put("list", list);
        return Result.ok(data);
    }
}

package com.qingmeili.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.MerchantPenalty;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.MerchantPenaltyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 暂停营业到期自动恢复：处罚单置为已结束（3），没有其他生效中的暂停则恢复商家营业。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyExpireTask {

    private final MerchantPenaltyMapper penaltyMapper;
    private final MerchantMapper merchantMapper;

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 45 * 1000)
    public void restoreExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<MerchantPenalty> expired = penaltyMapper.selectList(new LambdaQueryWrapper<MerchantPenalty>()
                .eq(MerchantPenalty::getType, "SUSPEND")
                .eq(MerchantPenalty::getStatus, 1)
                .isNotNull(MerchantPenalty::getEndTime)
                .le(MerchantPenalty::getEndTime, now));
        for (MerchantPenalty p : expired) {
            p.setStatus(3);
            penaltyMapper.updateById(p);
            Long active = penaltyMapper.selectCount(new LambdaQueryWrapper<MerchantPenalty>()
                    .eq(MerchantPenalty::getMerchantId, p.getMerchantId())
                    .eq(MerchantPenalty::getType, "SUSPEND")
                    .eq(MerchantPenalty::getStatus, 1)
                    .gt(MerchantPenalty::getEndTime, now));
            if (active == null || active == 0) {
                Merchant m = merchantMapper.selectById(p.getMerchantId());
                if (m != null) {
                    m.setBusinessStatus(1);
                    merchantMapper.updateById(m);
                    log.info("处罚到期，商家 {} 恢复营业", p.getMerchantId());
                }
            }
        }
    }
}

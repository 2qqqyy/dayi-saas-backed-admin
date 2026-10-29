package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.merchant.MerchantAuditDTO;
import com.qingmeili.entity.Merchant;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 商户服务实现
 */
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements MerchantService {

    @Override
    public Result<PageResult<Merchant>> page(Integer page, Integer pageSize, Integer auditStatus, String keyword) {
        Page<Merchant> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        if (auditStatus != null) {
            wrapper.eq(Merchant::getAuditStatus, auditStatus);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Merchant::getName, keyword);
        }
        wrapper.orderByDesc(Merchant::getCreateTime);
        Page<Merchant> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<Merchant> getDetail(Long id) {
        Merchant merchant = baseMapper.selectById(id);
        if (merchant == null) {
            throw new BusinessException("商户不存在");
        }
        return Result.ok(merchant);
    }

    @Override
    public Result<Void> audit(Long id, MerchantAuditDTO dto) {
        Merchant merchant = baseMapper.selectById(id);
        if (merchant == null) {
            throw new BusinessException("商户不存在");
        }
        if ("approve".equals(dto.getAction())) {
            merchant.setAuditStatus(1);
            merchant.setStatus(1);
        } else if ("reject".equals(dto.getAction())) {
            merchant.setAuditStatus(2);
            merchant.setRejectReason(dto.getReason());
            merchant.setRejectCount(merchant.getRejectCount() + 1);
        } else {
            throw new BusinessException("无效的操作类型");
        }
        baseMapper.updateById(merchant);
        return Result.ok("审核完成", null);
    }

    @Override
    public Result<Void> updateStatus(Long id, Integer status) {
        Merchant merchant = baseMapper.selectById(id);
        if (merchant == null) {
            throw new BusinessException("商户不存在");
        }
        merchant.setStatus(status);
        baseMapper.updateById(merchant);
        return Result.ok("状态更新成功", null);
    }
}

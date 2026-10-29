package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.picking.PickingSessionDTO;
import com.qingmeili.entity.PickingSession;
import com.qingmeili.mapper.PickingSessionMapper;
import com.qingmeili.service.PickingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 采摘档期服务实现
 */
@Service
@RequiredArgsConstructor
public class PickingSessionServiceImpl extends ServiceImpl<PickingSessionMapper, PickingSession> implements PickingSessionService {

    @Override
    public Result<List<PickingSession>> listByMerchant(Long merchantId, Integer status) {
        LambdaQueryWrapper<PickingSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PickingSession::getMerchantId, merchantId);
        if (status != null) {
            wrapper.eq(PickingSession::getStatus, status);
        }
        wrapper.orderByDesc(PickingSession::getSlotDate);
        List<PickingSession> list = baseMapper.selectList(wrapper);
        return Result.ok(list);
    }

    @Override
    public Result<PickingSession> create(PickingSessionDTO dto) {
        PickingSession session = new PickingSession();
        session.setMerchantId(UserContext.getMerchantId());
        session.setOrchardId(dto.getOrchardId());
        session.setSlotDate(dto.getSlotDate());
        session.setTimeRange(dto.getTimeRange() != null ? dto.getTimeRange() : "09:00-11:00");
        session.setMaxPeople(dto.getMaxPeople() != null ? dto.getMaxPeople() : 30);
        session.setBookedCount(0);
        session.setPrice(dto.getPrice());
        session.setAuditStatus(0);
        // 支持存草稿：0=草稿（C端不可见） 1=已发布，未传时默认发布
        session.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        baseMapper.insert(session);
        return Result.ok("创建成功", session);
    }

    @Override
    public Result<PickingSession> update(Long id, PickingSessionDTO dto) {
        PickingSession session = baseMapper.selectById(id);
        if (session == null) {
            throw new BusinessException("档期不存在");
        }
        if (dto.getOrchardId() != null) session.setOrchardId(dto.getOrchardId());
        if (dto.getSlotDate() != null) session.setSlotDate(dto.getSlotDate());
        if (dto.getTimeRange() != null) session.setTimeRange(dto.getTimeRange());
        if (dto.getMaxPeople() != null) session.setMaxPeople(dto.getMaxPeople());
        if (dto.getPrice() != null) session.setPrice(dto.getPrice());
        baseMapper.updateById(session);
        return Result.ok("修改成功", session);
    }

    @Override
    public Result<Void> updateStatus(Long id, Integer status) {
        PickingSession session = baseMapper.selectById(id);
        if (session == null) {
            throw new BusinessException("档期不存在");
        }
        session.setStatus(status);
        baseMapper.updateById(session);
        return Result.ok("状态更新成功", null);
    }

    @Override
    public Result<Void> delete(Long id) {
        PickingSession session = baseMapper.selectById(id);
        if (session == null) {
            throw new BusinessException("档期不存在");
        }
        baseMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}

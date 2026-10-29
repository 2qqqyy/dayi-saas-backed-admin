package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.picking.OrchardDTO;
import com.qingmeili.entity.Orchard;
import com.qingmeili.mapper.OrchardMapper;
import com.qingmeili.mapper.PickingSessionMapper;
import com.qingmeili.service.OrchardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 果园服务实现
 */
@Service
@RequiredArgsConstructor
public class OrchardServiceImpl extends ServiceImpl<OrchardMapper, Orchard> implements OrchardService {

    private final PickingSessionMapper pickingSessionMapper;

    @Override
    public Result<PageResult<Orchard>> page(Integer page, Integer pageSize, String keyword) {
        Page<Orchard> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Orchard> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Orchard::getName, keyword);
        }
        wrapper.orderByDesc(Orchard::getCreateTime);
        Page<Orchard> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<java.util.Map<String, Object>> getDetail(Long id) {
        Orchard orchard = baseMapper.selectById(id);
        if (orchard == null) {
            throw new BusinessException("果园不存在");
        }
        // 附带可预约场次（已通过审核且上架、未过期）
        java.util.List<com.qingmeili.entity.PickingSession> sessions = pickingSessionMapper.selectList(
                new LambdaQueryWrapper<com.qingmeili.entity.PickingSession>()
                        .eq(com.qingmeili.entity.PickingSession::getOrchardId, id)
                        .eq(com.qingmeili.entity.PickingSession::getStatus, 1)
                        .ge(com.qingmeili.entity.PickingSession::getSlotDate, java.time.LocalDate.now())
                        .orderByAsc(com.qingmeili.entity.PickingSession::getSlotDate));
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("orchard", orchard);
        data.put("sessions", sessions);
        return Result.ok(data);
    }

    @Override
    public Result<Orchard> create(OrchardDTO dto) {
        Orchard orchard = new Orchard();
        orchard.setMerchantId(UserContext.getMerchantId());
        orchard.setName(dto.getName());
        orchard.setAddress(dto.getAddress());
        orchard.setLat(dto.getLat());
        orchard.setLng(dto.getLng());
        orchard.setCoverImage(dto.getCoverImage());
        orchard.setArea(dto.getArea());
        orchard.setIntro(dto.getIntro());
        orchard.setNotices(dto.getNotices());
        orchard.setRefundRule(dto.getRefundRule());
        orchard.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        baseMapper.insert(orchard);
        return Result.ok("创建成功", orchard);
    }

    @Override
    public Result<Orchard> update(Long id, OrchardDTO dto) {
        Orchard orchard = baseMapper.selectById(id);
        if (orchard == null) {
            throw new BusinessException("果园不存在");
        }
        if (dto.getName() != null) orchard.setName(dto.getName());
        if (dto.getAddress() != null) orchard.setAddress(dto.getAddress());
        if (dto.getLat() != null) orchard.setLat(dto.getLat());
        if (dto.getLng() != null) orchard.setLng(dto.getLng());
        if (dto.getCoverImage() != null) orchard.setCoverImage(dto.getCoverImage());
        if (dto.getArea() != null) orchard.setArea(dto.getArea());
        if (dto.getIntro() != null) orchard.setIntro(dto.getIntro());
        if (dto.getNotices() != null) orchard.setNotices(dto.getNotices());
        if (dto.getRefundRule() != null) orchard.setRefundRule(dto.getRefundRule());
        if (dto.getStatus() != null) orchard.setStatus(dto.getStatus());
        baseMapper.updateById(orchard);
        return Result.ok("修改成功", orchard);
    }

    @Override
    public Result<Void> delete(Long id) {
        Orchard orchard = baseMapper.selectById(id);
        if (orchard == null) {
            throw new BusinessException("果园不存在");
        }
        baseMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}

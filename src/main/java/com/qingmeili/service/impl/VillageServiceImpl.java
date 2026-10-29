package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.village.VillageDTO;
import com.qingmeili.entity.Village;
import com.qingmeili.mapper.VillageMapper;
import com.qingmeili.service.VillageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 乡村服务实现
 */
@Service
@RequiredArgsConstructor
public class VillageServiceImpl extends ServiceImpl<VillageMapper, Village> implements VillageService {

    @Override
    public Result<PageResult<Village>> page(Integer page, Integer pageSize, String keyword) {
        Page<Village> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Village> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Village::getName, keyword);
        }
        wrapper.orderByDesc(Village::getCreateTime);
        Page<Village> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<Village> getDetail(Long id) {
        Village village = baseMapper.selectById(id);
        if (village == null) {
            throw new BusinessException("乡村不存在");
        }
        return Result.ok(village);
    }

    @Override
    public Result<Village> create(VillageDTO dto) {
        Village village = new Village();
        village.setName(dto.getName());
        village.setLocation(dto.getLocation());
        village.setLat(dto.getLat());
        village.setLng(dto.getLng());
        village.setCoverImage(dto.getCoverImage());
        village.setIntro(dto.getIntro());
        village.setArea(dto.getArea());
        village.setPopulation(dto.getPopulation());
        village.setHighlights(dto.getHighlights());
        village.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        baseMapper.insert(village);
        return Result.ok("创建成功", village);
    }

    @Override
    public Result<Village> update(Long id, VillageDTO dto) {
        Village village = baseMapper.selectById(id);
        if (village == null) {
            throw new BusinessException("乡村不存在");
        }
        if (dto.getName() != null) village.setName(dto.getName());
        if (dto.getLocation() != null) village.setLocation(dto.getLocation());
        if (dto.getLat() != null) village.setLat(dto.getLat());
        if (dto.getLng() != null) village.setLng(dto.getLng());
        if (dto.getCoverImage() != null) village.setCoverImage(dto.getCoverImage());
        if (dto.getIntro() != null) village.setIntro(dto.getIntro());
        if (dto.getArea() != null) village.setArea(dto.getArea());
        if (dto.getPopulation() != null) village.setPopulation(dto.getPopulation());
        if (dto.getHighlights() != null) village.setHighlights(dto.getHighlights());
        if (dto.getStatus() != null) village.setStatus(dto.getStatus());
        baseMapper.updateById(village);
        return Result.ok("修改成功", village);
    }

    @Override
    public Result<Void> delete(Long id) {
        Village village = baseMapper.selectById(id);
        if (village == null) {
            throw new BusinessException("乡村不存在");
        }
        baseMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}

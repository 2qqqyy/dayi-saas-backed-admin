package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Scenic;
import com.qingmeili.mapper.ScenicMapper;
import com.qingmeili.service.ScenicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 景点服务实现
 */
@Service
@RequiredArgsConstructor
public class ScenicServiceImpl extends ServiceImpl<ScenicMapper, Scenic> implements ScenicService {

    @Override
    public Result<PageResult<Scenic>> page(Integer page, Integer pageSize, String keyword, String category) {
        Page<Scenic> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Scenic> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Scenic::getName, keyword);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Scenic::getCategory, category);
        }
        wrapper.orderByAsc(Scenic::getSort).orderByDesc(Scenic::getCreateTime);
        Page<Scenic> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<Scenic> getDetail(Long id) {
        Scenic scenic = baseMapper.selectById(id);
        if (scenic == null) {
            throw new BusinessException("景点不存在");
        }
        return Result.ok(scenic);
    }

    @Override
    public Result<Scenic> create(Scenic scenic) {
        scenic.setStatus(1);
        baseMapper.insert(scenic);
        return Result.ok("创建成功", scenic);
    }

    @Override
    public Result<Scenic> update(Long id, Scenic scenic) {
        Scenic existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("景点不存在");
        }
        scenic.setId(id);
        baseMapper.updateById(scenic);
        return Result.ok("修改成功", baseMapper.selectById(id));
    }

    @Override
    public Result<Void> delete(Long id) {
        Scenic scenic = baseMapper.selectById(id);
        if (scenic == null) {
            throw new BusinessException("景点不存在");
        }
        baseMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}

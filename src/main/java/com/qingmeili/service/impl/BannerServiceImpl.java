package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Banner;
import com.qingmeili.mapper.BannerMapper;
import com.qingmeili.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 轮播图服务实现
 */
@Service
@RequiredArgsConstructor
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    @Override
    public Result<List<Banner>> listAll() {
        List<Banner> banners = baseMapper.selectList(
                new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, 1)
                        .orderByAsc(Banner::getSort)
                        .orderByDesc(Banner::getCreateTime));
        return Result.ok(banners);
    }

    @Override
    public Result<Banner> create(Banner banner) {
        banner.setStatus(1);
        baseMapper.insert(banner);
        return Result.ok("创建成功", banner);
    }

    @Override
    public Result<Banner> update(Long id, Banner banner) {
        Banner existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("轮播图不存在");
        }
        banner.setId(id);
        baseMapper.updateById(banner);
        return Result.ok("修改成功", baseMapper.selectById(id));
    }

    @Override
    public Result<Void> delete(Long id) {
        Banner banner = baseMapper.selectById(id);
        if (banner == null) {
            throw new BusinessException("轮播图不存在");
        }
        baseMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}

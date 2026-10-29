package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.favorite.FavoriteDTO;
import com.qingmeili.entity.Favorite;
import com.qingmeili.mapper.FavoriteMapper;
import com.qingmeili.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 收藏服务实现
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    @Override
    public Result<List<Favorite>> list(Long userId, String type) {
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Favorite::getType, type);
        }
        wrapper.orderByDesc(Favorite::getCreateTime);
        List<Favorite> list = baseMapper.selectList(wrapper);
        return Result.ok(list);
    }

    @Override
    public Result<Favorite> add(Long userId, FavoriteDTO dto) {
        if (dto.getType() == null || dto.getType().trim().isEmpty()) {
            throw new BusinessException("收藏类型不能为空");
        }
        // 检查是否已收藏
        Long count = baseMapper.selectCount(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getTargetId, dto.getTargetId())
                        .eq(Favorite::getType, dto.getType()));
        if (count > 0) {
            throw new BusinessException("已收藏该对象");
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetId(dto.getTargetId());
        favorite.setType(dto.getType().trim());
        favorite.setTitle(dto.getTitle());
        favorite.setSubtitle(dto.getSubtitle());
        favorite.setImage(dto.getImage());
        baseMapper.insert(favorite);
        return Result.ok("收藏成功", favorite);
    }

    @Override
    public Result<Void> remove(Long id, Long userId) {
        Favorite favorite = baseMapper.selectById(id);
        if (favorite == null) {
            throw new BusinessException("收藏不存在");
        }
        if (!favorite.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        baseMapper.deleteOwned(id, userId);
        return Result.ok("取消收藏", null);
    }
}

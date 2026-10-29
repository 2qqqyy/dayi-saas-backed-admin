package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.favorite.FavoriteDTO;
import com.qingmeili.entity.Favorite;

import java.util.List;

/**
 * 收藏服务
 */
public interface FavoriteService extends IService<Favorite> {

    Result<List<Favorite>> list(Long userId, String type);

    Result<Favorite> add(Long userId, FavoriteDTO dto);

    Result<Void> remove(Long id, Long userId);
}

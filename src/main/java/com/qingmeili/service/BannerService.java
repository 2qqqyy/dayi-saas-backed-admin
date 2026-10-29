package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Banner;

import java.util.List;

/**
 * 轮播图服务
 */
public interface BannerService extends IService<Banner> {

    Result<List<Banner>> listAll();

    Result<Banner> create(Banner banner);

    Result<Banner> update(Long id, Banner banner);

    Result<Void> delete(Long id);
}

package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Scenic;

/**
 * 景点服务
 */
public interface ScenicService extends IService<Scenic> {

    Result<PageResult<Scenic>> page(Integer page, Integer pageSize, String keyword, String category);

    Result<Scenic> getDetail(Long id);

    Result<Scenic> create(Scenic scenic);

    Result<Scenic> update(Long id, Scenic scenic);

    Result<Void> delete(Long id);
}

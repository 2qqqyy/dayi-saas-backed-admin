package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.village.VillageDTO;
import com.qingmeili.entity.Village;

/**
 * 乡村服务
 */
public interface VillageService extends IService<Village> {

    Result<PageResult<Village>> page(Integer page, Integer pageSize, String keyword);

    Result<Village> getDetail(Long id);

    Result<Village> create(VillageDTO dto);

    Result<Village> update(Long id, VillageDTO dto);

    Result<Void> delete(Long id);
}

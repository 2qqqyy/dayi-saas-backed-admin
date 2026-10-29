package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.picking.OrchardDTO;
import com.qingmeili.entity.Orchard;

/**
 * 果园服务
 */
public interface OrchardService extends IService<Orchard> {

    Result<PageResult<Orchard>> page(Integer page, Integer pageSize, String keyword);

    Result<java.util.Map<String, Object>> getDetail(Long id);

    Result<Orchard> create(OrchardDTO dto);

    Result<Orchard> update(Long id, OrchardDTO dto);

    Result<Void> delete(Long id);
}

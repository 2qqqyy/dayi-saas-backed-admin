package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Homestay;
import com.qingmeili.entity.HomestayRoom;

import java.util.List;
import java.util.Map;

/**
 * 民宿服务
 */
public interface HomestayService extends IService<Homestay> {

    Result<PageResult<Map<String, Object>>> page(Integer page, Integer pageSize, String keyword);

    Result<Map<String, Object>> getDetail(Long id);

    Result<Homestay> create(Homestay homestay);

    Result<Homestay> update(Long id, Homestay homestay);

    Result<Void> delete(Long id);
}

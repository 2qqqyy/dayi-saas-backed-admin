package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.TravelRoute;

import java.util.List;

/**
 * 旅游线路服务
 */
public interface TravelRouteService extends IService<TravelRoute> {

    Result<List<TravelRoute>> listActive();

    Result<TravelRoute> getDetail(Long id);
}

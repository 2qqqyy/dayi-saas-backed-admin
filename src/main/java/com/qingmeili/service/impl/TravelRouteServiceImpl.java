package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.TravelRoute;
import com.qingmeili.mapper.TravelRouteMapper;
import com.qingmeili.service.TravelRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 旅游线路服务实现
 */
@Service
@RequiredArgsConstructor
public class TravelRouteServiceImpl extends ServiceImpl<TravelRouteMapper, TravelRoute> implements TravelRouteService {

    @Override
    public Result<List<TravelRoute>> listActive() {
        LambdaQueryWrapper<TravelRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelRoute::getStatus, 1)
               .orderByAsc(TravelRoute::getSort);
        return Result.ok(baseMapper.selectList(wrapper));
    }

    @Override
    public Result<TravelRoute> getDetail(Long id) {
        TravelRoute route = baseMapper.selectById(id);
        if (route == null) {
            return Result.fail("线路不存在");
        }
        return Result.ok(route);
    }
}

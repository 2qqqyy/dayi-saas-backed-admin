package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.TravelRoute;
import com.qingmeili.mapper.TravelRouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台管理 - 旅游线路
 */
@RestController
@RequestMapping("/api/admin/routes")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminRouteController {

    private final TravelRouteMapper travelRouteMapper;

    @GetMapping
    public Result<List<TravelRoute>> list() {
        return Result.ok(travelRouteMapper.selectList(
                new LambdaQueryWrapper<TravelRoute>().orderByAsc(TravelRoute::getSort)));
    }

    @PostMapping
    public Result<TravelRoute> create(@RequestBody TravelRoute route) {
        if (!StringUtils.hasText(route.getTitle())) throw new BusinessException("线路名称不能为空");
        if (route.getStatus() == null) route.setStatus(1);
        if (route.getSort() == null) route.setSort(0);
        travelRouteMapper.insert(route);
        return Result.ok("创建成功", route);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TravelRoute body) {
        TravelRoute route = travelRouteMapper.selectById(id);
        if (route == null) throw new BusinessException("线路不存在");
        if (body.getTitle() != null) route.setTitle(body.getTitle());
        if (body.getCoverImage() != null) route.setCoverImage(body.getCoverImage());
        if (body.getDuration() != null) route.setDuration(body.getDuration());
        if (body.getBestTime() != null) route.setBestTime(body.getBestTime());
        if (body.getDistance() != null) route.setDistance(body.getDistance());
        if (body.getSummary() != null) route.setSummary(body.getSummary());
        if (body.getStops() != null) route.setStops(body.getStops());
        if (body.getSort() != null) route.setSort(body.getSort());
        if (body.getStatus() != null) route.setStatus(body.getStatus());
        travelRouteMapper.updateById(route);
        return Result.ok("更新成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        TravelRoute route = travelRouteMapper.selectById(id);
        if (route == null) throw new BusinessException("线路不存在");
        route.setStatus(status);
        travelRouteMapper.updateById(route);
        return Result.ok("状态更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        TravelRoute route = travelRouteMapper.selectById(id);
        if (route == null) throw new BusinessException("线路不存在");
        travelRouteMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}

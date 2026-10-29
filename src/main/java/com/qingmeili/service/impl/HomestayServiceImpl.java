package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Homestay;
import com.qingmeili.entity.HomestayRoom;
import com.qingmeili.mapper.HomestayMapper;
import com.qingmeili.mapper.HomestayRoomMapper;
import com.qingmeili.service.HomestayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 民宿服务实现
 */
@Service
@RequiredArgsConstructor
public class HomestayServiceImpl extends ServiceImpl<HomestayMapper, Homestay> implements HomestayService {

    private final HomestayRoomMapper homestayRoomMapper;

    @Override
    public Result<PageResult<Map<String, Object>>> page(Integer page, Integer pageSize, String keyword) {
        Page<Homestay> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Homestay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Homestay::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Homestay::getName, keyword);
        }
        wrapper.orderByDesc(Homestay::getCreateTime);
        Page<Homestay> result = baseMapper.selectPage(pageParam, wrapper);

        // 列表页需要展示房型与最低价，逐条附带在售房型
        List<Map<String, Object>> list = result.getRecords().stream().map(h -> {
            List<HomestayRoom> rooms = homestayRoomMapper.selectList(
                    new LambdaQueryWrapper<HomestayRoom>()
                            .eq(HomestayRoom::getHomestayId, h.getId())
                            .eq(HomestayRoom::getStatus, 1));
            Map<String, Object> item = new HashMap<>();
            item.put("homestay", h);
            item.put("rooms", rooms);
            return item;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    @Override
    public Result<Map<String, Object>> getDetail(Long id) {
        Homestay homestay = baseMapper.selectById(id);
        if (homestay == null) {
            throw new BusinessException("民宿不存在");
        }
        List<HomestayRoom> rooms = homestayRoomMapper.selectList(
                new LambdaQueryWrapper<HomestayRoom>()
                        .eq(HomestayRoom::getHomestayId, id)
                        .eq(HomestayRoom::getStatus, 1));
        Map<String, Object> data = new HashMap<>();
        data.put("homestay", homestay);
        data.put("rooms", rooms);
        return Result.ok(data);
    }

    @Override
    public Result<Homestay> create(Homestay homestay) {
        homestay.setMerchantId(UserContext.getMerchantId());
        homestay.setAuditStatus(0);
        // 新建民宿默认下架，待管理员审核通过后（audit 会置 status=1）再对 C 端可见
        homestay.setStatus(0);
        baseMapper.insert(homestay);
        return Result.ok("创建成功", homestay);
    }

    @Override
    public Result<Homestay> update(Long id, Homestay homestay) {
        Homestay existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("民宿不存在");
        }
        homestay.setId(id);
        baseMapper.updateById(homestay);
        return Result.ok("修改成功", baseMapper.selectById(id));
    }

    @Override
    public Result<Void> delete(Long id) {
        Homestay homestay = baseMapper.selectById(id);
        if (homestay == null) {
            throw new BusinessException("民宿不存在");
        }
        baseMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}

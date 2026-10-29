package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Homestay;
import com.qingmeili.entity.HomestayRoom;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.Order;
import com.qingmeili.mapper.HomestayMapper;
import com.qingmeili.mapper.HomestayRoomMapper;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台管理 - 民宿 / 房型
 */
@RestController
@RequestMapping("/api/admin")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminHomestayManageController {

    private final HomestayMapper homestayMapper;
    private final HomestayRoomMapper homestayRoomMapper;
    private final MerchantMapper merchantMapper;
    private final OrderMapper orderMapper;

    @GetMapping("/homestays")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Homestay> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Homestay> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) wrapper.like(Homestay::getName, keyword);
        if (auditStatus != null) wrapper.eq(Homestay::getAuditStatus, auditStatus);
        wrapper.orderByDesc(Homestay::getCreateTime);
        Page<Homestay> result = homestayMapper.selectPage(pageParam, wrapper);

        Map<Long, String> merchantNames = merchantMapper.selectList(null).stream()
                .collect(Collectors.toMap(Merchant::getId, Merchant::getName, (a, b) -> a));

        List<Map<String, Object>> list = result.getRecords().stream().map(h -> {
            List<HomestayRoom> rooms = homestayRoomMapper.selectList(
                    new LambdaQueryWrapper<HomestayRoom>().eq(HomestayRoom::getHomestayId, h.getId()));
            int roomCount = rooms.stream().mapToInt(r -> r.getStock() != null ? r.getStock() : 0).sum();
            Long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getMerchantId, h.getMerchantId()).eq(Order::getType, 2));
            Map<String, Object> m = new HashMap<>();
            m.put("id", h.getId());
            m.put("name", h.getName());
            m.put("merchantId", h.getMerchantId());
            m.put("merchantName", merchantNames.getOrDefault(h.getMerchantId(), ""));
            m.put("roomTypeCount", rooms.size());
            m.put("roomCount", roomCount);
            m.put("rating", h.getRating());
            m.put("orderCount", orderCount);
            m.put("auditStatus", h.getAuditStatus());
            m.put("status", h.getStatus());
            m.put("coverImage", h.getCoverImage());
            m.put("address", h.getAddress());
            return m;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** 民宿审核：approve 通过后自动上架，reject 驳回并下架 */
    @PostMapping("/homestays/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestParam String action) {
        Homestay homestay = homestayMapper.selectById(id);
        if (homestay == null) throw new BusinessException("民宿不存在");
        if ("approve".equalsIgnoreCase(action)) {
            homestay.setAuditStatus(1);
            homestay.setStatus(1);
        } else if ("reject".equalsIgnoreCase(action)) {
            homestay.setAuditStatus(2);
            homestay.setStatus(0);
        } else {
            throw new BusinessException("action 仅支持 approve / reject");
        }
        homestayMapper.updateById(homestay);
        return Result.ok("审核完成", null);
    }

    @PutMapping("/homestays/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Homestay homestay = homestayMapper.selectById(id);
        if (homestay == null) throw new BusinessException("民宿不存在");
        if (status == 1 && (homestay.getAuditStatus() == null || homestay.getAuditStatus() != 1)) {
            throw new BusinessException("民宿未通过审核，不能上架");
        }
        homestay.setStatus(status);
        homestayMapper.updateById(homestay);
        return Result.ok("状态更新成功", null);
    }

    /** 房型列表（可按民宿过滤） */
    @GetMapping("/homestay-rooms")
    public Result<List<Map<String, Object>>> rooms(@RequestParam(required = false) Long homestayId) {
        LambdaQueryWrapper<HomestayRoom> wrapper = new LambdaQueryWrapper<>();
        if (homestayId != null) wrapper.eq(HomestayRoom::getHomestayId, homestayId);
        wrapper.orderByAsc(HomestayRoom::getHomestayId).orderByAsc(HomestayRoom::getId);
        List<HomestayRoom> rooms = homestayRoomMapper.selectList(wrapper);

        Map<Long, String> homestayNames = homestayMapper.selectList(null).stream()
                .collect(Collectors.toMap(Homestay::getId, Homestay::getName, (a, b) -> a));

        List<Map<String, Object>> list = rooms.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("homestayId", r.getHomestayId());
            m.put("homestayName", homestayNames.getOrDefault(r.getHomestayId(), ""));
            m.put("name", r.getName());
            m.put("image", r.getImage());
            m.put("bedType", r.getBedType());
            m.put("capacity", r.getCapacity());
            m.put("area", r.getArea());
            m.put("breakfast", r.getBreakfast());
            m.put("stock", r.getStock());
            m.put("price", r.getPrice());
            m.put("status", r.getStatus());
            return m;
        }).toList();
        return Result.ok(list);
    }

    @PostMapping("/homestay-rooms")
    public Result<Map<String, Object>> createRoom(@RequestBody HomestayRoom room) {
        if (room.getHomestayId() == null) throw new BusinessException("homestayId 不能为空");
        if (!StringUtils.hasText(room.getName())) throw new BusinessException("房型名称不能为空");
        if (room.getStatus() == null) room.setStatus(1);
        if (room.getStock() == null) room.setStock(0);
        homestayRoomMapper.insert(room);
        Map<String, Object> m = new HashMap<>();
        m.put("id", room.getId());
        return Result.ok("创建成功", m);
    }

    @PutMapping("/homestay-rooms/{id}")
    public Result<Void> updateRoom(@PathVariable Long id, @RequestBody HomestayRoom body) {
        HomestayRoom room = homestayRoomMapper.selectById(id);
        if (room == null) throw new BusinessException("房型不存在");
        if (body.getName() != null) room.setName(body.getName());
        if (body.getImage() != null) room.setImage(body.getImage());
        if (body.getBedType() != null) room.setBedType(body.getBedType());
        if (body.getCapacity() != null) room.setCapacity(body.getCapacity());
        if (body.getArea() != null) room.setArea(body.getArea());
        if (body.getBreakfast() != null) room.setBreakfast(body.getBreakfast());
        if (body.getPrice() != null) room.setPrice(body.getPrice());
        if (body.getStock() != null) room.setStock(body.getStock());
        if (body.getStatus() != null) room.setStatus(body.getStatus());
        homestayRoomMapper.updateById(room);
        return Result.ok("更新成功", null);
    }

    @PutMapping("/homestay-rooms/{id}/stock")
    public Result<Void> updateRoomStock(@PathVariable Long id, @RequestParam Integer stock) {
        HomestayRoom room = homestayRoomMapper.selectById(id);
        if (room == null) throw new BusinessException("房型不存在");
        room.setStock(stock);
        homestayRoomMapper.updateById(room);
        return Result.ok("库存已更新", null);
    }

    @PutMapping("/homestay-rooms/{id}/status")
    public Result<Void> updateRoomStatus(@PathVariable Long id, @RequestParam Integer status) {
        HomestayRoom room = homestayRoomMapper.selectById(id);
        if (room == null) throw new BusinessException("房型不存在");
        room.setStatus(status);
        homestayRoomMapper.updateById(room);
        return Result.ok("状态更新成功", null);
    }
}

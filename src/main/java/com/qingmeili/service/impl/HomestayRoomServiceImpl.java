package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.homestay.HomestayRoomDTO;
import com.qingmeili.dto.homestay.RoomCalendarDTO;
import com.qingmeili.entity.HomestayRoom;
import com.qingmeili.entity.RoomCalendar;
import com.qingmeili.mapper.HomestayRoomMapper;
import com.qingmeili.mapper.RoomCalendarMapper;
import com.qingmeili.service.HomestayRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 民宿房型服务实现
 */
@Service
@RequiredArgsConstructor
public class HomestayRoomServiceImpl extends ServiceImpl<HomestayRoomMapper, HomestayRoom> implements HomestayRoomService {

    private final RoomCalendarMapper roomCalendarMapper;

    @Override
    public Result<List<HomestayRoom>> listByHomestay(Long homestayId) {
        List<HomestayRoom> rooms = baseMapper.selectList(
                new LambdaQueryWrapper<HomestayRoom>()
                        .eq(HomestayRoom::getHomestayId, homestayId)
                        .orderByAsc(HomestayRoom::getPrice));
        return Result.ok(rooms);
    }

    @Override
    public Result<HomestayRoom> create(HomestayRoomDTO dto) {
        HomestayRoom room = new HomestayRoom();
        room.setHomestayId(dto.getHomestayId());
        room.setName(dto.getName());
        room.setImage(dto.getImage());
        room.setCapacity(dto.getCapacity() != null ? dto.getCapacity() : 2);
        room.setBedType(dto.getBedType());
        room.setBreakfast(dto.getBreakfast());
        room.setPrice(dto.getPrice());
        room.setStock(dto.getStock() != null ? dto.getStock() : 1);
        room.setArea(dto.getArea());
        // 新建房型默认待审核（下架），审核/上架后对 C 端可见
        room.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
        baseMapper.insert(room);
        return Result.ok("创建成功", room);
    }

    @Override
    public Result<HomestayRoom> update(Long id, HomestayRoomDTO dto) {
        HomestayRoom room = baseMapper.selectById(id);
        if (room == null) {
            throw new BusinessException("房型不存在");
        }
        if (dto.getName() != null) room.setName(dto.getName());
        if (dto.getImage() != null) room.setImage(dto.getImage());
        if (dto.getCapacity() != null) room.setCapacity(dto.getCapacity());
        if (dto.getBedType() != null) room.setBedType(dto.getBedType());
        if (dto.getBreakfast() != null) room.setBreakfast(dto.getBreakfast());
        if (dto.getPrice() != null) room.setPrice(dto.getPrice());
        if (dto.getStock() != null) room.setStock(dto.getStock());
        if (dto.getArea() != null) room.setArea(dto.getArea());
        if (dto.getStatus() != null) room.setStatus(dto.getStatus());
        baseMapper.updateById(room);
        return Result.ok("修改成功", room);
    }

    @Override
    public Result<Void> updateStatus(Long id, Integer status) {
        HomestayRoom room = baseMapper.selectById(id);
        if (room == null) {
            throw new BusinessException("房型不存在");
        }
        room.setStatus(status);
        baseMapper.updateById(room);
        return Result.ok("状态更新成功", null);
    }

    @Override
    @Transactional
    public Result<List<RoomCalendar>> updateCalendar(Long roomId, RoomCalendarDTO dto) {
        HomestayRoom room = baseMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房型不存在");
        }
        for (var date : dto.getDates()) {
            RoomCalendar existing = roomCalendarMapper.selectOne(
                    new LambdaQueryWrapper<RoomCalendar>()
                            .eq(RoomCalendar::getRoomId, roomId)
                            .eq(RoomCalendar::getRoomDate, date));
            if (existing != null) {
                existing.setStatus(dto.getStatus());
                if (dto.getPrice() != null) existing.setPrice(dto.getPrice());
                if (dto.getStock() != null) existing.setStock(dto.getStock());
                roomCalendarMapper.updateById(existing);
            } else {
                RoomCalendar cal = new RoomCalendar();
                cal.setRoomId(roomId);
                cal.setRoomDate(date);
                cal.setStatus(dto.getStatus());
                cal.setPrice(dto.getPrice() != null ? dto.getPrice() : room.getPrice());
                cal.setStock(dto.getStock() != null ? dto.getStock() : room.getStock());
                roomCalendarMapper.insert(cal);
            }
        }
        List<RoomCalendar> calendars = roomCalendarMapper.selectList(
                new LambdaQueryWrapper<RoomCalendar>()
                        .eq(RoomCalendar::getRoomId, roomId)
                        .orderByAsc(RoomCalendar::getRoomDate));
        return Result.ok(calendars);
    }
}

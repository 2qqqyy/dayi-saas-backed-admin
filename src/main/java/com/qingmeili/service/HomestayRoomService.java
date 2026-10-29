package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.homestay.HomestayRoomDTO;
import com.qingmeili.dto.homestay.RoomCalendarDTO;
import com.qingmeili.entity.HomestayRoom;
import com.qingmeili.entity.RoomCalendar;

import java.util.List;

/**
 * 民宿房型服务
 */
public interface HomestayRoomService extends IService<HomestayRoom> {

    Result<List<HomestayRoom>> listByHomestay(Long homestayId);

    Result<HomestayRoom> create(HomestayRoomDTO dto);

    Result<HomestayRoom> update(Long id, HomestayRoomDTO dto);

    Result<Void> updateStatus(Long id, Integer status);

    Result<List<RoomCalendar>> updateCalendar(Long roomId, RoomCalendarDTO dto);
}

package com.qingmeili.controller.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.homestay.HomestayRoomDTO;
import com.qingmeili.dto.homestay.RoomCalendarDTO;
import com.qingmeili.entity.Homestay;
import com.qingmeili.entity.HomestayRoom;
import com.qingmeili.entity.RoomCalendar;
import com.qingmeili.mapper.HomestayMapper;
import com.qingmeili.mapper.HomestayRoomMapper;
import com.qingmeili.mapper.RoomCalendarMapper;
import com.qingmeili.service.HomestayRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 商户端 - 民宿房型管理
 *
 * 说明：原接口 listByHomestay 需要显式传 homestayId，但商家端 H5 只知道自己
 * 的登录身份，不知道 homestayId。因此这里增加「按当前商户聚合」的查询接口。
 */
@RestController
@RequestMapping("/api/merchant/homestay")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantHomestayController {

    private final HomestayRoomService homestayRoomService;
    private final HomestayMapper homestayMapper;
    private final HomestayRoomMapper homestayRoomMapper;
    private final RoomCalendarMapper roomCalendarMapper;

    /** 当前商户名下的民宿列表 */
    @GetMapping("/list")
    public Result<List<Homestay>> myHomestays() {
        List<Homestay> list = homestayMapper.selectList(
                new LambdaQueryWrapper<Homestay>().eq(Homestay::getMerchantId, UserContext.getMerchantId()));
        return Result.ok(list);
    }

    /** 当前商户名下所有房型（跨其全部民宿聚合） */
    @GetMapping("/rooms")
    public Result<List<HomestayRoom>> myRooms(@RequestParam(required = false) Long homestayId) {
        if (homestayId != null) {
            return homestayRoomService.listByHomestay(homestayId);
        }
        List<Homestay> homestays = homestayMapper.selectList(
                new LambdaQueryWrapper<Homestay>().eq(Homestay::getMerchantId, UserContext.getMerchantId()));
        if (homestays.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }
        List<Long> ids = homestays.stream().map(Homestay::getId).toList();
        List<HomestayRoom> rooms = homestayRoomMapper.selectList(
                new LambdaQueryWrapper<HomestayRoom>()
                        .in(HomestayRoom::getHomestayId, ids)
                        .orderByAsc(HomestayRoom::getPrice));
        return Result.ok(rooms);
    }

    @PostMapping("/rooms")
    @OperationLog(module = "民宿房型", action = "创建房型")
    public Result<HomestayRoom> create(@Valid @RequestBody HomestayRoomDTO dto) {
        requireOwnedHomestay(dto.getHomestayId());
        return homestayRoomService.create(dto);
    }

    @PutMapping("/rooms/{id}")
    @OperationLog(module = "民宿房型", action = "编辑房型")
    public Result<HomestayRoom> update(@PathVariable Long id, @Valid @RequestBody HomestayRoomDTO dto) {
        requireOwnedRoom(id);
        return homestayRoomService.update(id, dto);
    }

    @PutMapping("/rooms/{id}/status")
    @OperationLog(module = "民宿房型", action = "更新房型状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        requireOwnedRoom(id);
        return homestayRoomService.updateStatus(id, status);
    }

    @DeleteMapping("/rooms/{id}")
    @OperationLog(module = "民宿房型", action = "删除房型")
    public Result<Void> delete(@PathVariable Long id) {
        requireOwnedRoom(id);
        homestayRoomMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }

    /** 房历查询 */
    @GetMapping("/rooms/{roomId}/calendar")
    public Result<List<RoomCalendar>> getCalendar(@PathVariable Long roomId) {
        requireOwnedRoom(roomId);
        List<RoomCalendar> list = roomCalendarMapper.selectList(
                new LambdaQueryWrapper<RoomCalendar>()
                        .eq(RoomCalendar::getRoomId, roomId)
                        .orderByAsc(RoomCalendar::getRoomDate));
        return Result.ok(list);
    }

    @PostMapping("/rooms/{roomId}/calendar")
    @OperationLog(module = "民宿房型", action = "更新房历")
    public Result<List<RoomCalendar>> updateCalendar(@PathVariable Long roomId, @Valid @RequestBody RoomCalendarDTO dto) {
        requireOwnedRoom(roomId);
        return homestayRoomService.updateCalendar(roomId, dto);
    }

    /**
     * 房型/房历都是按 id 直接寻址，绕过了 list 的 merchantId 过滤，
     * 归属需经 room → homestay → merchantId 反查确认。
     */
    private void requireOwnedRoom(Long roomId) {
        HomestayRoom room = homestayRoomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房型不存在");
        }
        requireOwnedHomestay(room.getHomestayId());
    }

    private void requireOwnedHomestay(Long homestayId) {
        Homestay homestay = homestayId == null ? null : homestayMapper.selectById(homestayId);
        if (homestay == null || !Objects.equals(homestay.getMerchantId(), UserContext.getMerchantId())) {
            throw new BusinessException("民宿不存在");
        }
    }
}

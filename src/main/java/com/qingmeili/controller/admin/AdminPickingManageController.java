package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.*;
import com.qingmeili.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台管理 - 采摘项目（果园）/ 档期 / 预约核销
 */
@RestController
@RequestMapping("/api/admin/picking")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminPickingManageController {

    private final OrchardMapper orchardMapper;
    private final PickingSessionMapper pickingSessionMapper;
    private final MerchantMapper merchantMapper;
    private final OrderMapper orderMapper;
    private final OrderPickingMapper orderPickingMapper;
    private final UserMapper userMapper;
    private final com.qingmeili.service.OrderService orderService;
    private final com.qingmeili.service.UserMessageService userMessageService;

    /* ---------------- 采摘项目（果园） ---------------- */

    @GetMapping("/projects")
    public Result<PageResult<Map<String, Object>>> projects(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Orchard> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Orchard> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) wrapper.like(Orchard::getName, keyword);
        if (status != null) wrapper.eq(Orchard::getStatus, status);
        wrapper.orderByDesc(Orchard::getCreateTime);
        Page<Orchard> result = orchardMapper.selectPage(pageParam, wrapper);

        Map<Long, String> merchantNames = merchantMapper.selectList(null).stream()
                .collect(Collectors.toMap(Merchant::getId, Merchant::getName, (a, b) -> a));

        List<Map<String, Object>> list = result.getRecords().stream().map(o -> {
            List<PickingSession> sessions = pickingSessionMapper.selectList(
                    new LambdaQueryWrapper<PickingSession>().eq(PickingSession::getOrchardId, o.getId()));
            BigDecimal price = sessions.stream().map(PickingSession::getPrice)
                    .filter(p -> p != null).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("name", o.getName());
            m.put("merchantId", o.getMerchantId());
            m.put("merchantName", merchantNames.getOrDefault(o.getMerchantId(), ""));
            m.put("address", o.getAddress());
            m.put("coverImage", o.getCoverImage());
            m.put("price", price);
            m.put("status", o.getStatus());
            m.put("sessionCount", sessions.size());
            m.put("bookingCount", countOrchardBookings(sessions));
            m.put("createdAt", o.getCreateTime());
            return m;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** 预约量按果园自己的档期统计：商家维度的 type=1 订单会把同商家其它果园的单算进来 */
    private Long countOrchardBookings(List<PickingSession> sessions) {
        List<Long> sessionIds = sessions.stream().map(PickingSession::getId).filter(Objects::nonNull).toList();
        if (sessionIds.isEmpty()) return 0L;
        List<Long> orderIds = orderPickingMapper.selectList(
                        new LambdaQueryWrapper<OrderPicking>().in(OrderPicking::getSessionId, sessionIds))
                .stream().map(OrderPicking::getOrderId).filter(Objects::nonNull).distinct().toList();
        if (orderIds.isEmpty()) return 0L;
        return orderMapper.selectCount(new LambdaQueryWrapper<Order>().in(Order::getId, orderIds));
    }

    /** 项目上下架：status 1上架 0下架 */
    @PutMapping("/projects/{id}/status")
    public Result<Void> updateProjectStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status == null || (status != 0 && status != 1)) throw new BusinessException("项目状态不合法");
        Orchard orchard = orchardMapper.selectById(id);
        if (orchard == null) throw new BusinessException("采摘项目不存在");
        orchard.setStatus(status);
        orchardMapper.updateById(orchard);
        return Result.ok("状态更新成功", null);
    }

    /* ---------------- 采摘档期 ---------------- */

    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions(@RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<PickingSession> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) wrapper.eq(PickingSession::getOrchardId, projectId);
        wrapper.orderByAsc(PickingSession::getSlotDate);
        List<PickingSession> sessions = pickingSessionMapper.selectList(wrapper);

        Map<Long, String> orchardNames = orchardMapper.selectList(null).stream()
                .collect(Collectors.toMap(Orchard::getId, Orchard::getName, (a, b) -> a));

        List<Map<String, Object>> list = sessions.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("projectId", s.getOrchardId());
            m.put("projectName", orchardNames.getOrDefault(s.getOrchardId(), ""));
            m.put("date", s.getSlotDate() != null ? s.getSlotDate().toString() : "");
            m.put("timeRange", s.getTimeRange());
            m.put("maxPeople", s.getMaxPeople());
            m.put("booked", s.getBookedCount());
            m.put("price", s.getPrice());
            m.put("auditStatus", s.getAuditStatus());
            m.put("status", s.getStatus());
            return m;
        }).toList();
        return Result.ok(list);
    }

    @PostMapping("/sessions")
    public Result<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
        PickingSession s = new PickingSession();
        Long orchardId = body.get("projectId") != null ? Long.valueOf(String.valueOf(body.get("projectId")))
                : body.get("orchardId") != null ? Long.valueOf(String.valueOf(body.get("orchardId"))) : null;
        if (orchardId == null) throw new BusinessException("请选择采摘项目");
        Orchard orchard = orchardMapper.selectById(orchardId);
        if (orchard == null) throw new BusinessException("采摘项目不存在");
        if (body.get("date") == null || String.valueOf(body.get("date")).isBlank()) {
            throw new BusinessException("请选择档期日期");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(String.valueOf(body.get("date")));
        } catch (Exception e) {
            throw new BusinessException("日期格式应为 YYYY-MM-DD");
        }
        if (date.isBefore(LocalDate.now())) throw new BusinessException("档期日期不能早于今天");

        String timeRange = String.valueOf(body.getOrDefault("timeRange", "09:00-17:00")).trim();
        String[] range = timeRange.split("-");
        if (range.length != 2 || range[0].isBlank() || range[1].isBlank()) {
            throw new BusinessException("时间段格式应为 09:00-17:00");
        }
        if (range[0].compareTo(range[1]) >= 0) throw new BusinessException("结束时间必须晚于开始时间");

        int maxPeople;
        try {
            maxPeople = Integer.parseInt(String.valueOf(body.getOrDefault("maxPeople", "20")));
        } catch (NumberFormatException e) {
            throw new BusinessException("最大人数必须为整数");
        }
        if (maxPeople < 1 || maxPeople > 999) throw new BusinessException("最大人数需在 1-999 之间");

        BigDecimal price;
        try {
            price = new BigDecimal(String.valueOf(body.getOrDefault("price", "0")));
        } catch (NumberFormatException e) {
            throw new BusinessException("价格格式不正确");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("价格必须大于 0");

        s.setOrchardId(orchardId);
        s.setMerchantId(orchard.getMerchantId());
        s.setSlotDate(date);
        s.setTimeRange(range[0].trim() + "-" + range[1].trim());
        s.setMaxPeople(maxPeople);
        s.setBookedCount(0);
        s.setPrice(price);
        s.setAuditStatus(1);
        s.setStatus(1);
        pickingSessionMapper.insert(s);

        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("projectId", s.getOrchardId());
        m.put("projectName", orchard.getName());
        m.put("date", s.getSlotDate().toString());
        m.put("timeRange", s.getTimeRange());
        m.put("maxPeople", s.getMaxPeople());
        m.put("booked", 0);
        m.put("price", s.getPrice());
        m.put("status", s.getStatus());
        return Result.ok("创建成功", m);
    }

    /** 档期状态：1开放 0关闭 2已满 */
    @PutMapping("/sessions/{id}/status")
    public Result<Void> updateSessionStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status == null || status < 0 || status > 2) throw new BusinessException("档期状态不合法");
        PickingSession s = pickingSessionMapper.selectById(id);
        if (s == null) throw new BusinessException("档期不存在");
        s.setStatus(status);
        pickingSessionMapper.updateById(s);
        return Result.ok("状态更新成功", null);
    }

    /* ---------------- 采摘预约（订单 type=1） ---------------- */

    @GetMapping("/bookings")
    public Result<PageResult<Map<String, Object>>> bookings(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Order> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getType, 1);
        if (StringUtils.hasText(keyword)) wrapper.like(Order::getOrderNo, keyword);
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> result = orderMapper.selectPage(pageParam, wrapper);

        Map<Long, PickingSession> sessionMap = pickingSessionMapper.selectList(null).stream()
                .collect(Collectors.toMap(PickingSession::getId, s -> s, (a, b) -> a));

        List<Map<String, Object>> list = result.getRecords().stream().map(o -> {
            OrderPicking picking = orderPickingMapper.selectOne(
                    new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, o.getId()));
            User user = o.getUserId() != null ? userMapper.selectById(o.getUserId()) : null;
            PickingSession session = picking != null && picking.getSessionId() != null
                    ? sessionMap.get(picking.getSessionId()) : null;
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("bookingNo", o.getOrderNo());
            m.put("user", user != null ? user.getNickname() : "游客");
            m.put("phone", picking != null && picking.getContactMobile() != null ? picking.getContactMobile()
                    : user != null ? user.getMobile() : "");
            m.put("project", o.getTitle());
            String sessionText = "";
            if (picking != null && picking.getPickDate() != null) {
                sessionText = picking.getPickDate().toString()
                        + (session != null && session.getTimeRange() != null ? " " + session.getTimeRange() : "");
            }
            m.put("session", sessionText);
            m.put("people", picking != null && picking.getPeopleNum() != null ? picking.getPeopleNum() : 0);
            m.put("amount", o.getAmount());
            m.put("payStatus", o.getPayAmount() != null ? "PAID" : "UNPAID");
            m.put("orderStatus", o.getStatus());
            m.put("verified", picking != null && picking.getVerifyTime() != null);
            m.put("createdAt", o.getCreateTime());
            return m;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** 预约核销：待使用 → 已完成 */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/bookings/{id}/verify")
    public Result<Void> verify(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null || order.getType() == null || order.getType() != 1) {
            throw new BusinessException("采摘预约不存在");
        }
        if (!"pending_use".equals(order.getStatus())) {
            throw new BusinessException("当前状态不支持核销");
        }
        order.setStatus("completed");
        orderMapper.updateById(order);
        OrderPicking picking = orderPickingMapper.selectOne(
                new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, id));
        if (picking != null) {
            picking.setVerifyTime(java.time.LocalDateTime.now());
            orderPickingMapper.updateById(picking);
        }
        orderService.writeSettlement(order);
        userMessageService.notifyOrderEvent(order, "verified");
        return Result.ok("核销成功", null);
    }
}

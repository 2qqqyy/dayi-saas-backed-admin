package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Address;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.User;
import com.qingmeili.mapper.AddressMapper;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理 - C 端用户管理
 */
@RestController
@RequestMapping("/api/admin/c-users")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminCUserController {

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final AddressMapper addressMapper;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<User> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getNickname, keyword).or().like(User::getMobile, keyword));
        }
        if (status != null) wrapper.eq(User::getStatus, status);
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> result = userMapper.selectPage(pageParam, wrapper);

        // 一次性批量查询当前页用户的订单，避免循环内逐个查询（N+1）
        List<Long> userIds = result.getRecords().stream().map(User::getId).toList();
        Map<Long, List<Order>> ordersByUser = userIds.isEmpty() ? Map.of()
                : orderMapper.selectList(new LambdaQueryWrapper<Order>().in(Order::getUserId, userIds))
                        .stream().collect(java.util.stream.Collectors.groupingBy(Order::getUserId));

        List<Map<String, Object>> list = result.getRecords().stream().map(u -> {
            List<Order> orders = ordersByUser.getOrDefault(u.getId(), List.of());
            BigDecimal consume = orders.stream()
                    .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("phone", u.getMobile());
            m.put("level", u.getLevel());
            m.put("status", u.getStatus());
            m.put("orderCount", orders.size());
            m.put("consumeAmount", consume);
            m.put("createdAt", u.getCreateTime());
            m.put("lastLoginAt", u.getUpdateTime());
            return m;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("用户不存在");

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, id).orderByDesc(Order::getCreateTime));
        BigDecimal consume = orders.stream()
                .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Address addr = addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, id).orderByDesc(Address::getIsDefault).last("limit 1"));

        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("phone", u.getMobile());
        m.put("level", u.getLevel());
        m.put("status", u.getStatus());
        m.put("orderCount", orders.size());
        m.put("consumeAmount", consume);
        m.put("createdAt", u.getCreateTime());
        m.put("lastLoginAt", u.getUpdateTime());
        m.put("pickingCount", orders.stream().filter(o -> o.getType() != null && o.getType() == 1).count());
        m.put("homestayCount", orders.stream().filter(o -> o.getType() != null && o.getType() == 2).count());
        m.put("productCount", orders.stream().filter(o -> o.getType() != null && o.getType() == 3).count());
        m.put("address", addr != null
                ? String.join("", nullToEmpty(addr.getProvince()), nullToEmpty(addr.getCity()),
                        nullToEmpty(addr.getDistrict()), nullToEmpty(addr.getDetail()))
                : "");
        // 近期订单作为行为轨迹
        List<Map<String, Object>> behavior = orders.stream().limit(10).map(o -> {
            Map<String, Object> b = new HashMap<>();
            b.put("id", o.getId());
            b.put("action", "下单");
            b.put("target", o.getTitle() != null ? o.getTitle() : o.getOrderNo());
            b.put("time", o.getCreateTime());
            return b;
        }).toList();
        m.put("behavior", behavior);
        return Result.ok(m);
    }

    /** 启用/禁用：status 1正常 0禁用 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("用户不存在");
        u.setStatus(status);
        userMapper.updateById(u);
        return Result.ok("状态更新成功", null);
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}

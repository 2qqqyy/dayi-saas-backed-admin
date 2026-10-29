package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.User;
import com.qingmeili.entity.UserLevel;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.UserLevelMapper;
import com.qingmeili.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台管理 - 会员等级
 * 会员人数按 user.level 实时统计，并支持按订单实付金额重算全部用户等级。
 */
@RestController
@RequestMapping("/api/admin/levels")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminUserLevelController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserLevelMapper userLevelMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        Map<String, Long> counts = userMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(u -> StringUtils.hasText(u.getLevel()) ? u.getLevel() : "未设置",
                        Collectors.counting()));
        List<Map<String, Object>> list = levels().stream().map(level -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", level.getId());
            m.put("name", level.getName());
            m.put("threshold", nz(level.getThreshold()));
            m.put("minAmount", level.getMinAmount());
            m.put("sort", level.getSort());
            m.put("status", level.getStatus());
            m.put("memberCount", counts.getOrDefault(level.getName(), 0L));
            m.put("updatedAt", level.getUpdateTime() == null ? "" : level.getUpdateTime().format(TIME));
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    @PostMapping
    public Result<UserLevel> create(@RequestBody UserLevel body) {
        if (!StringUtils.hasText(body.getName())) throw new BusinessException("等级名称不能为空");
        if (exists(body.getName(), null)) throw new BusinessException("等级名称已存在");
        if (body.getMinAmount() == null) body.setMinAmount(BigDecimal.ZERO);
        if (body.getStatus() == null) body.setStatus(1);
        if (body.getSort() == null) body.setSort(99);
        if (!StringUtils.hasText(body.getThreshold())) {
            body.setThreshold("累计消费 ¥" + body.getMinAmount().stripTrailingZeros().toPlainString());
        }
        userLevelMapper.insert(body);
        return Result.ok("创建成功", body);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserLevel body) {
        UserLevel level = require(id);
        if (StringUtils.hasText(body.getName()) && !body.getName().equals(level.getName())) {
            if (exists(body.getName(), id)) throw new BusinessException("等级名称已存在");
            // 等级改名后同步已归属用户，避免出现统计不到的孤立等级
            User patch = new User();
            patch.setLevel(body.getName());
            userMapper.update(patch, new LambdaQueryWrapper<User>().eq(User::getLevel, level.getName()));
            level.setName(body.getName());
        }
        if (body.getThreshold() != null) level.setThreshold(body.getThreshold());
        if (body.getMinAmount() != null) level.setMinAmount(body.getMinAmount());
        if (body.getSort() != null) level.setSort(body.getSort());
        if (body.getStatus() != null) level.setStatus(body.getStatus());
        userLevelMapper.updateById(level);
        return Result.ok("更新成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        UserLevel level = require(id);
        level.setStatus(status);
        userLevelMapper.updateById(level);
        return Result.ok("状态已更新", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserLevel level = require(id);
        Long members = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getLevel, level.getName()));
        if (members != null && members > 0) throw new BusinessException("该等级下仍有 " + members + " 位会员，无法删除");
        userLevelMapper.deleteById(id);
        return Result.ok("已删除", null);
    }

    /** 按已支付订单实付金额重算所有用户等级 */
    @PostMapping("/recalculate")
    public Result<Map<String, Object>> recalculate() {
        List<UserLevel> ladders = levels().stream()
                .filter(l -> l.getStatus() != null && l.getStatus() == 1)
                .sorted(Comparator.comparing((UserLevel l) ->
                        l.getMinAmount() == null ? BigDecimal.ZERO : l.getMinAmount()).reversed())
                .toList();
        if (ladders.isEmpty()) throw new BusinessException("没有启用中的等级，无法重算");

        Map<Long, BigDecimal> spend = new HashMap<>();
        orderMapper.selectList(new LambdaQueryWrapper<Order>().isNotNull(Order::getPayTime))
                .forEach(o -> {
                    if (o.getUserId() == null) return;
                    BigDecimal paid = o.getPayAmount() != null ? o.getPayAmount() : nz(o.getAmount());
                    BigDecimal refund = nz(o.getRefundAmount());
                    spend.merge(o.getUserId(), paid.subtract(refund), BigDecimal::add);
                });

        String base = ladders.get(ladders.size() - 1).getName();
        int changed = 0;
        for (User user : userMapper.selectList(null)) {
            BigDecimal amount = spend.getOrDefault(user.getId(), BigDecimal.ZERO);
            String target = ladders.stream()
                    .filter(l -> amount.compareTo(nz(l.getMinAmount())) >= 0)
                    .map(UserLevel::getName)
                    .findFirst()
                    .orElse(base);
            if (!target.equals(user.getLevel())) {
                User patch = new User();
                patch.setId(user.getId());
                patch.setLevel(target);
                userMapper.updateById(patch);
                changed++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", userMapper.selectCount(null));
        data.put("changed", changed);
        data.put("baseLevel", base);
        return Result.ok("等级已重算", data);
    }

    private List<UserLevel> levels() {
        return userLevelMapper.selectList(new LambdaQueryWrapper<UserLevel>()
                .orderByAsc(UserLevel::getSort)
                .orderByAsc(UserLevel::getId));
    }

    private UserLevel require(Long id) {
        UserLevel level = userLevelMapper.selectById(id);
        if (level == null) throw new BusinessException("等级不存在");
        return level;
    }

    private boolean exists(String name, Long excludeId) {
        LambdaQueryWrapper<UserLevel> wrapper = new LambdaQueryWrapper<UserLevel>().eq(UserLevel::getName, name);
        if (excludeId != null) wrapper.ne(UserLevel::getId, excludeId);
        Long count = userLevelMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}

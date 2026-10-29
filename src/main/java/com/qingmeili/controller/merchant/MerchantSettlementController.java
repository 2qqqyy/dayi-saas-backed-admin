package com.qingmeili.controller.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.Settlement;
import com.qingmeili.entity.Withdrawal;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 商户端 - 结算、提现与经营统计
 */
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantSettlementController {

    private final SettlementService settlementService;
    private final OrderMapper orderMapper;

    /** 结算列表 */
    @GetMapping("/settlements")
    public Result<PageResult<Settlement>> settlements(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return settlementService.page(UserContext.getMerchantId(), page, pageSize);
    }

    @PostMapping("/withdrawals")
    @OperationLog(module = "提现", action = "申请提现")
    public Result<Withdrawal> applyWithdrawal(@RequestParam BigDecimal amount) {
        return settlementService.applyWithdrawal(UserContext.getMerchantId(), amount);
    }

    @GetMapping("/withdrawals")
    public Result<PageResult<Withdrawal>> withdrawals(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return settlementService.listWithdrawals(UserContext.getMerchantId(), page, pageSize);
    }

    /** 结算口径概览（基于 settlement 表） */
    @GetMapping("/statistics/overview")
    public Result<Map<String, Object>> statisticsOverview() {
        return settlementService.statisticsOverview(UserContext.getMerchantId());
    }

    /**
     * 经营统计（基于订单实时聚合，供商家端「经营概览」页使用）
     * @param range today | week | month | all
     */
    @GetMapping("/statistics/summary")
    public Result<Map<String, Object>> statisticsSummary(
            @RequestParam(defaultValue = "today") String range) {
        Long merchantId = UserContext.getMerchantId();
        LocalDateTime from = resolveFrom(range);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getMerchantId, merchantId);
        if (from != null) {
            wrapper.ge(Order::getCreateTime, from);
        }
        List<Order> orders = orderMapper.selectList(wrapper);

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        int orderCount = 0;
        int verifyCount = 0;
        int waitVerify = 0;

        // 分类营业额（采摘 / 民宿 / 商品）
        Map<String, BigDecimal> categoryIncome = new LinkedHashMap<>();
        categoryIncome.put("采摘", BigDecimal.ZERO);
        categoryIncome.put("民宿", BigDecimal.ZERO);
        categoryIncome.put("商品", BigDecimal.ZERO);
        String[] catNames = {"采摘", "民宿", "商品"};

        // 近 7 日趋势
        Map<String, BigDecimal> trendMap = new LinkedHashMap<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            trendMap.put(LocalDate.now().minusDays(i).format(df), BigDecimal.ZERO);
        }

        for (Order o : orders) {
            String st = o.getStatus() == null ? "" : o.getStatus();
            BigDecimal amt = o.getPayAmount() != null ? o.getPayAmount()
                    : (o.getAmount() != null ? o.getAmount() : BigDecimal.ZERO);

            if ("completed".equals(st) || "shipping".equals(st)
                    || "pending_use".equals(st) || "pending_delivery".equals(st)) {
                revenue = revenue.add(amt);
                orderCount++;
                int idx = (o.getType() != null && o.getType() >= 1 && o.getType() <= 3) ? o.getType() - 1 : 2;
                categoryIncome.put(catNames[idx], categoryIncome.get(catNames[idx]).add(amt));
                if (o.getCreateTime() != null) {
                    String key = o.getCreateTime().toLocalDate().format(df);
                    if (trendMap.containsKey(key)) {
                        trendMap.put(key, trendMap.get(key).add(amt));
                    }
                }
            }
            if ("completed".equals(st)) {
                verifyCount++;
            }
            if ("pending_use".equals(st)) {
                waitVerify++;
            }
            if ("refunded".equals(st) || "refunding".equals(st)) {
                BigDecimal r = o.getRefundAmount() != null ? o.getRefundAmount() : amt;
                refundAmount = refundAmount.add(r);
            }
        }

        List<Map<String, Object>> revenueTrend = new ArrayList<>();
        trendMap.forEach((k, v) -> {
            Map<String, Object> p = new HashMap<>();
            p.put("date", k.substring(5));
            p.put("value", v);
            revenueTrend.add(p);
        });

        List<Map<String, Object>> categoryList = new ArrayList<>();
        categoryIncome.forEach((k, v) -> {
            Map<String, Object> p = new HashMap<>();
            p.put("name", k);
            p.put("value", v);
            categoryList.add(p);
        });

        Map<String, Object> data = new HashMap<>();
        data.put("revenue", revenue);
        data.put("orderCount", orderCount);
        data.put("verifyCount", verifyCount);
        data.put("waitVerify", waitVerify);
        data.put("refundAmount", refundAmount);
        data.put("revenueTrend", revenueTrend);
        data.put("categoryIncome", categoryList);
        return Result.ok(data);
    }

    /**
     * 工作台首页数据（待办 + 今日经营）
     */
    @GetMapping("/statistics/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long merchantId = UserContext.getMerchantId();

        // 待核销 / 待发货 / 待退款
        long waitVerify = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId).eq(Order::getStatus, "pending_use"));
        long waitShip = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId).eq(Order::getStatus, "pending_delivery"));
        long waitRefund = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId).eq(Order::getStatus, "refunding"));

        // 今日订单
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<Order> todayOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId)
                .ge(Order::getCreateTime, todayStart)
                .orderByDesc(Order::getCreateTime));

        BigDecimal todayRevenue = BigDecimal.ZERO;
        for (Order o : todayOrders) {
            String st = o.getStatus() == null ? "" : o.getStatus();
            if (!"cancelled".equals(st) && !"refunded".equals(st)) {
                todayRevenue = todayRevenue.add(
                        o.getPayAmount() != null ? o.getPayAmount()
                                : (o.getAmount() != null ? o.getAmount() : BigDecimal.ZERO));
            }
        }

        // 近 7 日趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            LocalDateTime s = d.atStartOfDay();
            LocalDateTime e = d.plusDays(1).atStartOfDay();
            List<Order> dayOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .eq(Order::getMerchantId, merchantId)
                    .ge(Order::getCreateTime, s).lt(Order::getCreateTime, e));
            BigDecimal sum = BigDecimal.ZERO;
            for (Order o : dayOrders) {
                String st = o.getStatus() == null ? "" : o.getStatus();
                if (!"cancelled".equals(st) && !"refunded".equals(st)) {
                    sum = sum.add(o.getPayAmount() != null ? o.getPayAmount()
                            : (o.getAmount() != null ? o.getAmount() : BigDecimal.ZERO));
                }
            }
            Map<String, Object> p = new HashMap<>();
            p.put("date", d.format(df).substring(5));
            p.put("value", sum);
            trend.add(p);
        }

        // 待办列表
        List<Map<String, Object>> todos = new ArrayList<>();
        todos.add(todo("verify", "待核销订单", waitVerify, "pages/order/index?tab=WAIT_VERIFY"));
        todos.add(todo("ship", "待发货订单", waitShip, "pages/order/index?tab=WAIT_SHIP"));
        todos.add(todo("refund", "待处理退款", waitRefund, "pages/order/index?tab=REFUNDING"));

        Map<String, Object> data = new HashMap<>();
        data.put("todayRevenue", todayRevenue);
        data.put("todayOrders", (long) todayOrders.size());
        data.put("waitVerify", waitVerify);
        data.put("waitShip", waitShip);
        data.put("todos", todos);
        data.put("todayOrderList", todayOrders.stream().limit(4).toList());
        data.put("trend", trend);
        return Result.ok(data);
    }

    private Map<String, Object> todo(String key, String label, long count, String path) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("count", count);
        m.put("path", path);
        return m;
    }

    private LocalDateTime resolveFrom(String range) {
        if (range == null) return LocalDate.now().atStartOfDay();
        switch (range.toLowerCase()) {
            case "today":
                return LocalDate.now().atStartOfDay();
            case "week":
                return LocalDate.now().minusDays(6).atStartOfDay();
            case "month":
                return LocalDate.now().withDayOfMonth(1).atStartOfDay();
            case "all":
                return null;
            default:
                return LocalDate.now().atStartOfDay();
        }
    }
}

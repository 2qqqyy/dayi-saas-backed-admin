package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.Product;
import com.qingmeili.entity.ProductSku;
import com.qingmeili.entity.User;
import com.qingmeili.entity.UserFeedback;
import com.qingmeili.mapper.HomestayMapper;
import com.qingmeili.mapper.HomestayRoomMapper;
import com.qingmeili.mapper.OrderItemMapper;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.PickingSessionMapper;
import com.qingmeili.mapper.ProductMapper;
import com.qingmeili.mapper.ProductCategoryMapper;
import com.qingmeili.mapper.ProductSkuMapper;
import com.qingmeili.mapper.UserFeedbackMapper;
import com.qingmeili.mapper.UserMapper;
import com.qingmeili.entity.Merchant;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仪表盘服务实现
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;
    private final HomestayMapper homestayMapper;
    private final PickingSessionMapper pickingSessionMapper;
    private final HomestayRoomMapper homestayRoomMapper;
    private final ProductSkuMapper productSkuMapper;
    private final UserFeedbackMapper userFeedbackMapper;
    private final ProductCategoryMapper productCategoryMapper;

    @Override
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();

        // 总订单数
        Long totalOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>());
        data.put("orderCount", totalOrders);

        // 总销售额
        List<Order> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .isNotNull(Order::getPayAmount));
        BigDecimal totalSales = paidOrders.stream()
                .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("salesAmount", totalSales);

        // 预约单数（采摘 + 民宿订单）
        Long bookingCount = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getType, 1, 2));
        data.put("bookingCount", bookingCount);

        // 活跃商户数（审核通过且状态正常）
        Long merchantActive = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getStatus, 1));
        data.put("merchantActive", merchantActive);

        // 补充总量指标，便于看板扩展展示
        data.put("totalUsers", userMapper.selectCount(new LambdaQueryWrapper<User>()));
        data.put("totalMerchants", merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()));

        // 今日指标
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long todayOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, todayStart));
        data.put("todayOrders", todayOrders);
        BigDecimal todayAmount = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .ge(Order::getCreateTime, todayStart).isNotNull(Order::getPayAmount))
                .stream().map(Order::getPayAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("todayAmount", todayAmount);
        Long todayUsers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, todayStart));
        data.put("todayUsers", todayUsers);
        data.put("todayBookings", orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, todayStart).in(Order::getType, 1, 2)));

        // 资源总量
        data.put("productCount", productMapper.selectCount(new LambdaQueryWrapper<Product>()));
        data.put("homestayCount", homestayMapper.selectCount(
                new LambdaQueryWrapper<com.qingmeili.entity.Homestay>()));

        // 订单类型分布：1采摘 2民宿 3商品
        List<Map<String, Object>> orderTypeDist = new ArrayList<>();
        String[] typeNames = {"采摘预约", "民宿预订", "商品订单"};
        for (int t = 1; t <= 3; t++) {
            Long c = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getType, t));
            Map<String, Object> item = new HashMap<>();
            item.put("name", typeNames[t - 1]);
            item.put("value", c);
            orderTypeDist.add(item);
        }
        data.put("orderTypeDist", orderTypeDist);

        // 近 7 日用户增长
        List<String> dates = new ArrayList<>();
        List<Long> newUsers = new ArrayList<>();
        List<Long> activeUsers = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            dates.add(d.toString());
            Long c = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .ge(User::getCreateTime, d.atStartOfDay())
                    .lt(User::getCreateTime, d.plusDays(1).atStartOfDay()));
            newUsers.add(c);
            Long active = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .ge(Order::getCreateTime, d.atStartOfDay())
                    .lt(Order::getCreateTime, d.plusDays(1).atStartOfDay()));
            activeUsers.add(active);
        }
        Map<String, Object> userGrowth = new HashMap<>();
        userGrowth.put("dates", dates);
        userGrowth.put("newUsers", newUsers);
        userGrowth.put("activeUsers", activeUsers);
        data.put("userGrowth", userGrowth);

        // 热销商品 TOP5
        List<Map<String, Object>> hotItems = productMapper.selectList(
                        new LambdaQueryWrapper<Product>().orderByDesc(Product::getSales).last("limit 5"))
                .stream().map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", p.getName());
                    item.put("value", p.getSales() != null ? p.getSales() : 0);
                    return item;
                }).collect(Collectors.toList());
        data.put("hotItems", hotItems);

        // 待办事项
        List<Map<String, Object>> todos = new ArrayList<>();
        todos.add(todo("merchant", "待审核商户", merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getAuditStatus, 0)), "/merchant/audit"));
        todos.add(todo("product", "待审核商品", productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getAuditStatus, 0)), "/product/list"));
        todos.add(todo("session", "待审核档期", pickingSessionMapper.selectCount(
                new LambdaQueryWrapper<com.qingmeili.entity.PickingSession>()
                        .eq(com.qingmeili.entity.PickingSession::getAuditStatus, 0)), "/tourism/session"));
        todos.add(todo("room", "待审核房源", homestayRoomMapper.selectCount(
                new LambdaQueryWrapper<com.qingmeili.entity.HomestayRoom>()
                        .eq(com.qingmeili.entity.HomestayRoom::getStatus, 0)), "/tourism/homestay"));
        todos.add(todo("refund", "退款待处理", orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, "refunding")), "/order/refund"));
        data.put("todos", todos);

        data.put("statDate", LocalDate.now().toString());

        return Result.ok(data);
    }

    private Map<String, Object> todo(String key, String label, Long count, String path) {
        Map<String, Object> item = new HashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("count", count);
        item.put("path", path);
        return item;
    }

    /** 低于该库存值即视为「库存不足」，与后台库存管理页的标记口径一致 */
    private static final int LOW_STOCK = 10;

    @Override
    public Result<List<Map<String, Object>>> notifications() {
        List<Map<String, Object>> list = new ArrayList<>();

        long merchants = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getAuditStatus, 0));
        addNotice(list, "merchant", "AUDIT", "个商家待审核", merchants,
                merchants > 0 ? newestTime(merchantMapper, Merchant::getCreateTime,
                        new LambdaQueryWrapper<Merchant>().eq(Merchant::getAuditStatus, 0)) : null,
                "/merchant/audit");

        long products = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getAuditStatus, 0));
        addNotice(list, "product", "AUDIT", "个商品待审核", products,
                products > 0 ? newestTime(productMapper, Product::getCreateTime,
                        new LambdaQueryWrapper<Product>().eq(Product::getAuditStatus, 0)) : null,
                "/product/list");

        long refunds = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "refunding"));
        addNotice(list, "refund", "REFUND", "笔退款待处理", refunds,
                refunds > 0 ? newestTime(orderMapper, Order::getCreateTime,
                        new LambdaQueryWrapper<Order>().eq(Order::getStatus, "refunding")) : null,
                "/order/refund");

        long shipping = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "pending_delivery"));
        addNotice(list, "shipping", "ORDER", "笔订单待发货", shipping,
                shipping > 0 ? newestTime(orderMapper, Order::getCreateTime,
                        new LambdaQueryWrapper<Order>().eq(Order::getStatus, "pending_delivery")) : null,
                "/order/list");

        long verifying = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "pending_use"));
        addNotice(list, "verify", "ORDER", "笔订单待核销", verifying,
                verifying > 0 ? newestTime(orderMapper, Order::getCreateTime,
                        new LambdaQueryWrapper<Order>().eq(Order::getStatus, "pending_use")) : null,
                "/order/list");

        long lowStock = productSkuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                .lt(ProductSku::getStock, LOW_STOCK));
        addNotice(list, "stock", "SYSTEM", "个 SKU 库存不足", lowStock,
                lowStock > 0 ? newestTime(productSkuMapper, ProductSku::getUpdateTime,
                        new LambdaQueryWrapper<ProductSku>().lt(ProductSku::getStock, LOW_STOCK)) : null,
                "/product/inventory");

        long feedbacks = userFeedbackMapper.selectCount(new LambdaQueryWrapper<UserFeedback>()
                .eq(UserFeedback::getStatus, 0));
        addNotice(list, "feedback", "SYSTEM", "条意见反馈待回复", feedbacks,
                feedbacks > 0 ? newestTime(userFeedbackMapper, UserFeedback::getCreateTime,
                        new LambdaQueryWrapper<UserFeedback>().eq(UserFeedback::getStatus, 0)) : null,
                "/user/feedback");

        return Result.ok(list);
    }

    /** count 为 0 的事项不进消息列表，避免顶栏被无意义的提醒占满 */
    private void addNotice(List<Map<String, Object>> list, String key, String type, String noun,
                           long count, LocalDateTime latest, String path) {
        if (count <= 0) {
            return;
        }
        Map<String, Object> item = new HashMap<>();
        // id 用语义 key：数量变化时仍是同一条消息，前端的已读状态才不会失效
        item.put("id", key);
        item.put("type", type);
        item.put("title", "有 " + count + " " + noun);
        item.put("count", count);
        item.put("time", relativeTime(latest));
        item.put("path", path);
        list.add(item);
    }

    /** 取该批记录里最新一条的时间，用于把消息显示成「N 分钟前」 */
    private <T> LocalDateTime newestTime(BaseMapper<T> mapper, SFunction<T, LocalDateTime> timeCol,
                                         LambdaQueryWrapper<T> wrapper) {
        wrapper.select(timeCol).orderByDesc(timeCol).last("limit 1");
        T row = mapper.selectOne(wrapper);
        return row == null ? null : timeCol.apply(row);
    }

    private String relativeTime(LocalDateTime time) {
        if (time == null) {
            return "近期";
        }
        java.time.Duration span = java.time.Duration.between(time, LocalDateTime.now());
        if (span.toMinutes() < 1) {
            return "刚刚";
        }
        if (span.toMinutes() < 60) {
            return span.toMinutes() + " 分钟前";
        }
        if (span.toHours() < 24) {
            return span.toHours() + " 小时前";
        }
        if (span.toDays() < 30) {
            return span.toDays() + " 天前";
        }
        return time.toLocalDate().toString();
    }

    @Override
    public com.qingmeili.service.DashboardService.ReportFile exportReport(
            String type, String startDate, String endDate) {
        String kind = type == null || type.isBlank() ? "orders" : type.trim().toLowerCase();
        LocalDateTime from = parseDay(startDate, false);
        LocalDateTime to = parseDay(endDate, true);

        List<String> header;
        List<List<Object>> rows = new ArrayList<>();
        String label;

        switch (kind) {
            case "products" -> {
                label = "商品报表";
                header = List.of("商品ID", "名称", "分类", "价格", "销量", "库存",
                        "审核状态", "上架状态", "所属商户", "创建时间");
                List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .ge(from != null, Product::getCreateTime, from)
                        .le(to != null, Product::getCreateTime, to)
                        .orderByAsc(Product::getId));
                Map<Long, String> cats = new HashMap<>();
                productCategoryMapper.selectList(null)
                        .forEach(c -> cats.put(c.getId(), c.getName()));
                Map<Long, String> merchants = merchantNames(list.stream()
                        .map(Product::getMerchantId).filter(Objects::nonNull).distinct().toList());
                Map<Long, Integer> stock = new HashMap<>();
                if (!list.isEmpty()) {
                    productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                                    .in(ProductSku::getProductId,
                                            list.stream().map(Product::getId).toList()))
                            .forEach(sku -> stock.merge(sku.getProductId(),
                                    sku.getStock() == null ? 0 : sku.getStock(), Integer::sum));
                }
                for (Product p : list) {
                    rows.add(Arrays.asList(p.getId(), p.getName(),
                            cats.getOrDefault(p.getCategoryId(), ""), p.getPrice(), p.getSales(),
                            stock.getOrDefault(p.getId(), 0),
                            auditText(p.getAuditStatus()), onOff(p.getOnShelf()),
                            merchants.getOrDefault(p.getMerchantId(), ""), fmt(p.getCreateTime())));
                }
            }
            case "merchants" -> {
                label = "商户报表";
                header = List.of("商户ID", "名称", "联系人", "电话", "审核状态",
                        "营业状态", "评分", "入驻时间");
                List<Merchant> list = merchantMapper.selectList(new LambdaQueryWrapper<Merchant>()
                        .ge(from != null, Merchant::getCreateTime, from)
                        .le(to != null, Merchant::getCreateTime, to)
                        .orderByAsc(Merchant::getId));
                for (Merchant m : list) {
                    rows.add(Arrays.asList(m.getId(), m.getName(), m.getContact(), m.getMobile(),
                            auditText(m.getAuditStatus()),
                            Integer.valueOf(1).equals(m.getBusinessStatus()) ? "营业中" : "歇业",
                            m.getRating(), fmt(m.getCreateTime())));
                }
            }
            case "stats" -> {
                label = "经营日报";
                header = List.of("日期", "订单数", "销售额");
                LocalDateTime begin = LocalDate.now().minusDays(29).atStartOfDay();
                Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
                for (int i = 29; i >= 0; i--) {
                    byDay.put(LocalDate.now().minusDays(i), new long[2]);
                }
                for (Order o : orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .ge(Order::getCreateTime, begin))) {
                    if (o.getCreateTime() == null) {
                        continue;
                    }
                    long[] cell = byDay.get(o.getCreateTime().toLocalDate());
                    if (cell == null) {
                        continue;
                    }
                    cell[0] += 1;
                    cell[1] += o.getAmount() == null ? 0 : o.getAmount().movePointRight(2).longValue();
                }
                byDay.forEach((day, cell) ->
                        rows.add(Arrays.asList(day.toString(), cell[0],
                        BigDecimal.valueOf(cell[1]).movePointLeft(2))));
            }
            case "orders" -> {
                label = "订单报表";
                header = List.of("订单号", "类型", "标题", "所属商户", "下单用户",
                        "金额", "实付", "状态", "下单时间", "支付时间");
                List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .ge(from != null, Order::getCreateTime, from)
                        .le(to != null, Order::getCreateTime, to)
                        .orderByDesc(Order::getCreateTime));
                Map<Long, String> users = new HashMap<>();
                List<Long> userIds = list.stream().map(Order::getUserId)
                        .filter(Objects::nonNull).distinct().toList();
                if (!userIds.isEmpty()) {
                    userMapper.selectBatchIds(userIds)
                            .forEach(u -> users.put(u.getId(), nz(u.getNickname(), u.getMobile())));
                }
                Map<Long, String> merchants = merchantNames(list.stream()
                        .map(Order::getMerchantId).filter(Objects::nonNull).distinct().toList());
                for (Order o : list) {
                    rows.add(Arrays.asList(o.getOrderNo(), orderTypeText(o.getType()), o.getTitle(),
                            merchants.getOrDefault(o.getMerchantId(), ""),
                            users.getOrDefault(o.getUserId(), ""),
                            o.getAmount(), o.getPayAmount(), orderStatusText(o.getStatus()),
                            fmt(o.getCreateTime()), fmt(o.getPayTime())));
                }
            }
            default -> throw new BusinessException("不支持的报表类型：" + kind);
        }
        // Excel 需要 BOM 才会按 UTF-8 解析中文，缺了就是乱码
        byte[] content = ("﻿" + toCsv(header, rows)).getBytes(StandardCharsets.UTF_8);
        return new com.qingmeili.service.DashboardService.ReportFile(
                "青梅里" + label + "_" + LocalDate.now() + ".csv", content);
    }

    private Map<Long, String> merchantNames(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (!ids.isEmpty()) {
            merchantMapper.selectBatchIds(ids).forEach(m -> map.put(m.getId(), m.getName()));
        }
        return map;
    }

    private String toCsv(List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(header.stream().map(h -> "\"" + h.replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(","))).append("\r\n");
        for (List<Object> row : rows) {
            sb.append(row.stream()
                    .map(c -> c == null ? "" : String.valueOf(c))
                    .map(v -> "\"" + v.replace("\"", "\"\"") + "\"")
                    .collect(Collectors.joining(","))).append("\r\n");
        }
        return sb.toString();
    }

    /** 前端传 yyyy-MM-dd；endOfDay=true 时补到当天 23:59:59，保证「含结束日」 */
    private LocalDateTime parseDay(String text, boolean endOfDay) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            LocalDate day = LocalDate.parse(text.trim().substring(0, 10));
            return endOfDay ? day.atTime(23, 59, 59) : day.atStartOfDay();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String nz(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String orderTypeText(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case 1 -> "采摘";
            case 2 -> "民宿";
            default -> "商品";
        };
    }

    private String orderStatusText(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "pending_payment" -> "待付款";
            case "pending_delivery" -> "待发货";
            case "shipping", "shipped", "pending_receive" -> "待收货";
            case "pending_use" -> "待使用";
            case "completed" -> "已完成";
            case "refunding" -> "退款中";
            case "refunded", "refund_rejected" -> "已退款";
            case "cancelled" -> "已取消";
            default -> status;
        };
    }

    private String auditText(Integer auditStatus) {
        if (auditStatus == null) {
            return "";
        }
        return switch (auditStatus) {
            case 0 -> "待审核";
            case 1 -> "已通过";
            default -> "已驳回";
        };
    }

    private String onOff(Integer onShelf) {
        return Integer.valueOf(1).equals(onShelf) ? "上架" : "下架";
    }

    @Override
    public Result<List<Map<String, Object>>> trend(String metric, String range) {
        int days = switch (range != null ? range : "week") {
            case "month" -> 30;
            case "quarter" -> 90;
            default -> 7;
        };

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .ge(Order::getCreateTime, startDateTime)
                        .orderByAsc(Order::getCreateTime));

        Map<LocalDate, BigDecimal> dailyData = orders.stream()
                .filter(o -> o.getPayAmount() != null)
                .collect(Collectors.groupingBy(
                        o -> o.getCreateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Order::getPayAmount, BigDecimal::add)));

        boolean orderMetric = "order".equalsIgnoreCase(metric != null ? metric : "sales");
        Map<LocalDate, Long> dailyCounts = orderMetric ? orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getCreateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.counting())) : Map.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            item.put("value", orderMetric
                    ? BigDecimal.valueOf(dailyCounts.getOrDefault(date, 0L))
                    : dailyData.getOrDefault(date, BigDecimal.ZERO));
            result.add(item);
        }

        return Result.ok(result);
    }

    @Override
    public Result<List<Map<String, Object>>> merchantActivity(String range, Integer limit) {
        int days = switch (range != null ? range : "week") {
            case "today" -> 1;
            case "30d", "month" -> 30;
            default -> 7;
        };
        int top = limit != null && limit > 0 ? limit : 10;
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();

        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .ge(Order::getCreateTime, since)
                        .isNotNull(Order::getMerchantId));

        Map<Long, List<Order>> byMerchant = orders.stream()
                .collect(Collectors.groupingBy(Order::getMerchantId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<Order>> entry : byMerchant.entrySet()) {
            Long merchantId = entry.getKey();
            List<Order> merchantOrders = entry.getValue();
            Merchant merchant = merchantMapper.selectById(merchantId);

            Map<String, Object> item = new HashMap<>();
            item.put("merchantId", String.valueOf(merchantId));
            item.put("merchantName", merchant != null ? merchant.getName() : "商户" + merchantId);
            item.put("merchantType", merchant != null ? merchant.getType() : null);
            item.put("orderCount", merchantOrders.size());
            item.put("amount", merchantOrders.stream()
                    .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            item.put("lastActiveAt", merchantOrders.stream()
                    .map(Order::getCreateTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .map(LocalDateTime::toString)
                    .orElse(null));
            result.add(item);
        }

        result.sort((a, b) -> Integer.compare((Integer) b.get("orderCount"), (Integer) a.get("orderCount")));
        if (result.size() > top) {
            result = result.subList(0, top);
        }
        return Result.ok(result);
    }

    @Override
    public Result<Map<String, Object>> distribution() {
        // 订单状态 → 中文标签（仅输出库中实际存在的状态）
        Map<String, String> statusLabels = new LinkedHashMap<>();
        statusLabels.put("pending_payment", "待付款");
        statusLabels.put("pending_delivery", "待发货");
        statusLabels.put("shipping", "配送中");
        // 商家端发货写 shipped，后台历史数据两种都在用，缺一个就会在图表里漏出原始码
        statusLabels.put("shipped", "配送中");
        statusLabels.put("pending_receive", "待收货");
        statusLabels.put("pending_use", "待使用");
        statusLabels.put("completed", "已完成");
        statusLabels.put("refunding", "退款中");
        statusLabels.put("refunded", "已退款");
        statusLabels.put("refund_rejected", "退款驳回");
        statusLabels.put("cancelled", "已取消");

        List<Map<String, Object>> orderStatusDist = new ArrayList<>();
        for (Map<String, Object> row : orderMapper.selectStatusDist()) {
            String code = String.valueOf(row.get("name"));
            Map<String, Object> item = new HashMap<>();
            item.put("name", statusLabels.getOrDefault(code, code));
            item.put("value", row.get("value"));
            orderStatusDist.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("orderStatusDist", orderStatusDist);
        data.put("categorySalesDist", orderItemMapper.selectCategorySalesDist());
        return Result.ok(data);
    }
}

package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.common.util.RandomUtil;
import com.qingmeili.dto.order.HomestayOrderDTO;
import com.qingmeili.dto.order.PickingOrderDTO;
import com.qingmeili.dto.order.ProductOrderDTO;
import com.qingmeili.entity.*;
import com.qingmeili.mapper.*;
import com.qingmeili.service.OrderService;
import com.qingmeili.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 订单服务实现
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final OrderPickingMapper orderPickingMapper;
    private final OrderHomestayMapper orderHomestayMapper;
    private final OrderProductMapper orderProductMapper;
    private final OrderItemMapper orderItemMapper;
    private final PickingSessionMapper pickingSessionMapper;
    private final OrchardMapper orchardMapper;
    private final HomestayRoomMapper homestayRoomMapper;
    private final HomestayMapper homestayMapper;
    private final RoomCalendarMapper roomCalendarMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CartItemMapper cartItemMapper;
    private final AddressMapper addressMapper;
    private final MerchantMapper merchantMapper;
    private final SettlementMapper settlementMapper;
    private final SysConfigService sysConfigService;

    /** 读数字配置，缺省回退默认值：运费/预约规则改由后台 sys_config 维护 */
    private int configInt(String key, int def) {
        try {
            String v = sysConfigService.getValue(key).getData();
            return v == null || v.isBlank() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 歇业商家不允许被下单：否则游客付了钱商家却接不了单 */
    private void assertMerchantOpen(Long merchantId) {
        if (merchantId == null) {
            return;
        }
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant != null
                && ((merchant.getStatus() != null && merchant.getStatus() == 0)
                || (merchant.getBusinessStatus() != null && merchant.getBusinessStatus() == 0))) {
            throw new BusinessException("商家暂停营业，暂不可下单");
        }
    }

    private String generateOrderNo() {
        return "QM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + RandomUtil.randomNumbers(4);
    }

    @Override
    @Transactional
    public Result<Order> createPickingOrder(Long userId, PickingOrderDTO dto) {
        PickingSession session = pickingSessionMapper.selectById(dto.getSessionId());
        if (session == null) {
            throw new BusinessException("采摘档期不存在");
        }
        // 草稿/下架档期对 C 端不可见，也不能被下单，否则会出现「订到商家没放出的场次」
        if (session.getStatus() == null || session.getStatus() != 1) {
            throw new BusinessException("该场次未开放预约");
        }
        // 下单日期必须与档期日期一致：否则订单显示 A 天、核销按 B 天，商家对不上账
        if (!dto.getDate().equals(session.getSlotDate())) {
            throw new BusinessException("所选日期与该场次不符，请重新选择");
        }
        // 果园以档期归属为准，防止传入别的果园导致订单落到错误商户名下
        if (!Objects.equals(dto.getOrchardId(), session.getOrchardId())) {
            throw new BusinessException("场次与果园不匹配，请刷新后重试");
        }
        if (session.getBookedCount() + dto.getPeopleNum() > session.getMaxPeople()) {
            throw new BusinessException("预约人数超出剩余名额");
        }

        Orchard orchard = orchardMapper.selectById(dto.getOrchardId());
        if (orchard == null) {
            throw new BusinessException("果园不存在");
        }
        assertMerchantOpen(orchard.getMerchantId());
        // 预约提前天数由后台配置：booking.advance_days=0 表示当天可约
        int advance = configInt("booking.advance_days", 0);
        if (advance > 0 && ChronoUnit.DAYS.between(LocalDate.now(), dto.getDate()) < advance) {
            throw new BusinessException("该果园需提前 " + advance + " 天预约");
        }

        BigDecimal amount = session.getPrice().multiply(BigDecimal.valueOf(dto.getPeopleNum()));

        // 创建主订单
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setMerchantId(orchard.getMerchantId());
        order.setType(1); // 采摘
        order.setStatus("pending_payment");
        order.setTitle(orchard.getName());
        order.setSubtitle(dto.getDate() + " " + session.getTimeRange());
        order.setCoverImage(orchard.getCoverImage());
        order.setAmount(amount);
        order.setRemark("");
        order.setDeleted(0);
        baseMapper.insert(order);

        // 创建采摘订单扩展
        OrderPicking picking = new OrderPicking();
        picking.setOrderId(order.getId());
        picking.setSessionId(session.getId());
        picking.setOrchardId(orchard.getId());
        picking.setPeopleNum(dto.getPeopleNum());
        picking.setContactName(dto.getContactName());
        picking.setContactMobile(dto.getContactMobile());
        picking.setPickDate(dto.getDate());
        picking.setVerifyCode(RandomUtil.randomNumbers(6));
        orderPickingMapper.insert(picking);

        // 扣减档期名额
        session.setBookedCount(session.getBookedCount() + dto.getPeopleNum());
        pickingSessionMapper.updateById(session);

        return Result.ok("下单成功", order);
    }

    @Override
    @Transactional
    public Result<Order> createHomestayOrder(Long userId, HomestayOrderDTO dto) {
        HomestayRoom room = homestayRoomMapper.selectById(dto.getRoomId());
        if (room == null) {
            throw new BusinessException("房型不存在");
        }
        Homestay homestay = homestayMapper.selectById(dto.getHomestayId());
        if (homestay == null) {
            throw new BusinessException("民宿不存在");
        }
        assertMerchantOpen(homestay.getMerchantId());

        long nights = ChronoUnit.DAYS.between(dto.getCheckIn(), dto.getCheckOut());
        if (nights <= 0) {
            throw new BusinessException("退房日期必须晚于入住日期");
        }

        BigDecimal amount = room.getPrice().multiply(BigDecimal.valueOf(nights));

        // 扣减房态日历库存
        LocalDate date = dto.getCheckIn();
        while (date.isBefore(dto.getCheckOut())) {
            RoomCalendar cal = roomCalendarMapper.selectOne(
                    new LambdaQueryWrapper<RoomCalendar>()
                            .eq(RoomCalendar::getRoomId, room.getId())
                            .eq(RoomCalendar::getRoomDate, date));
            int stock = cal != null ? cal.getStock() : room.getStock();
            if (stock <= 0) {
                throw new BusinessException(date + "房间已满");
            }
            if (cal != null) {
                cal.setStock(stock - 1);
                roomCalendarMapper.updateById(cal);
            } else {
                RoomCalendar newCal = new RoomCalendar();
                newCal.setRoomId(room.getId());
                newCal.setRoomDate(date);
                newCal.setStock(stock - 1);
                newCal.setPrice(room.getPrice());
                newCal.setStatus(1);
                roomCalendarMapper.insert(newCal);
            }
            date = date.plusDays(1);
        }

        // 创建主订单
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setMerchantId(homestay.getMerchantId());
        order.setType(2); // 民宿
        order.setStatus("pending_payment");
        order.setTitle(homestay.getName());
        order.setSubtitle(dto.getCheckIn() + " ~ " + dto.getCheckOut());
        order.setCoverImage(room.getImage());
        order.setAmount(amount);
        order.setRemark("");
        order.setDeleted(0);
        baseMapper.insert(order);

        // 创建民宿订单扩展
        OrderHomestay homestayOrder = new OrderHomestay();
        homestayOrder.setOrderId(order.getId());
        homestayOrder.setHomestayId(homestay.getId());
        homestayOrder.setRoomId(room.getId());
        homestayOrder.setCheckIn(dto.getCheckIn());
        homestayOrder.setCheckOut(dto.getCheckOut());
        homestayOrder.setNights((int) nights);
        homestayOrder.setGuestName(dto.getGuestName());
        homestayOrder.setGuestMobile(dto.getGuestMobile());
        orderHomestayMapper.insert(homestayOrder);

        return Result.ok("下单成功", order);
    }

    @Override
    @Transactional
    public Result<Order> createProductOrder(Long userId, ProductOrderDTO dto) {
        Address address = addressMapper.selectById(dto.getAddressId());
        if (address == null) {
            throw new BusinessException("收货地址不存在");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Long merchantId = null;

        // 从购物车下单
        if (dto.getCartItemIds() != null && !dto.getCartItemIds().isEmpty()) {
            for (Long cartItemId : dto.getCartItemIds()) {
                CartItem cartItem = cartItemMapper.selectById(cartItemId);
                if (cartItem == null || !cartItem.getUserId().equals(userId)) {
                    throw new BusinessException("购物车项不存在");
                }
                ProductSku sku = productSkuMapper.selectById(cartItem.getSkuId());
                Product product = productMapper.selectById(cartItem.getProductId());
                if (sku == null || product == null) {
                    throw new BusinessException("商品不存在");
                }
                if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1) {
                    throw new BusinessException("商品数量必须大于 0");
                }
                if (cartItem.getQuantity() > 9999) {
                    throw new BusinessException("单次购买数量不能超过 9999");
                }
                if (sku.getStock() < cartItem.getQuantity()) {
                    throw new BusinessException(product.getName() + " 库存不足");
                }
                // 扣减库存
                sku.setStock(sku.getStock() - cartItem.getQuantity());
                sku.setSales(sku.getSales() + cartItem.getQuantity());
                productSkuMapper.updateById(sku);
                product.setSales(product.getSales() + cartItem.getQuantity());
                productMapper.updateById(product);

                merchantId = product.getMerchantId();

                OrderItem item = new OrderItem();
                item.setOrderId(0L); // 后面更新
                item.setProductId(product.getId());
                item.setSkuId(sku.getId());
                item.setProductName(product.getName());
                item.setSkuName(sku.getName());
                item.setImage(product.getCoverImage());
                item.setQuantity(cartItem.getQuantity());
                item.setUnitPrice(sku.getPrice());
                item.setDeleted(0);
                orderItems.add(item);
                totalAmount = totalAmount.add(sku.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            }
            // 删除购物车项
            cartItemMapper.deleteBatchIds(dto.getCartItemIds());
        }

        // 直接下单
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            for (ProductOrderDTO.OrderItemDTO itemDto : dto.getItems()) {
                if (itemDto.getQuantity() == null || itemDto.getQuantity() < 1) {
                    throw new BusinessException("商品数量必须大于 0");
                }
                if (itemDto.getQuantity() > 9999) {
                    throw new BusinessException("单次购买数量不能超过 9999");
                }
                ProductSku sku = productSkuMapper.selectById(itemDto.getSkuId());
                Product product = productMapper.selectById(itemDto.getProductId());
                if (sku == null || product == null) {
                    throw new BusinessException("商品不存在");
                }
                if (sku.getStock() < itemDto.getQuantity()) {
                    throw new BusinessException(product.getName() + " 库存不足");
                }
                sku.setStock(sku.getStock() - itemDto.getQuantity());
                sku.setSales(sku.getSales() + itemDto.getQuantity());
                productSkuMapper.updateById(sku);
                product.setSales(product.getSales() + itemDto.getQuantity());
                productMapper.updateById(product);

                merchantId = product.getMerchantId();

                OrderItem item = new OrderItem();
                item.setOrderId(0L);
                item.setProductId(product.getId());
                item.setSkuId(sku.getId());
                item.setProductName(product.getName());
                item.setSkuName(sku.getName());
                item.setImage(product.getCoverImage());
                item.setQuantity(itemDto.getQuantity());
                item.setUnitPrice(sku.getPrice());
                item.setDeleted(0);
                orderItems.add(item);
                totalAmount = totalAmount.add(sku.getPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity())));
            }
        }

        if (orderItems.isEmpty()) {
            throw new BusinessException("订单商品不能为空");
        }
        // 一个订单只能属于一个商户：混合购物车必须拆单，否则另一家的货没有归属
        java.util.Set<Long> merchantIds = new java.util.HashSet<>();
        for (OrderItem item : orderItems) {
            Product product = productMapper.selectById(item.getProductId());
            if (product != null && product.getMerchantId() != null) {
                merchantIds.add(product.getMerchantId());
            }
        }
        if (merchantIds.size() > 1) {
            throw new BusinessException("购物车含多个商家的商品，请分开下单");
        }
        if (!merchantIds.isEmpty()) {
            merchantId = merchantIds.iterator().next();
        }
        assertMerchantOpen(merchantId);

        // 创建主订单
        String addressSnapshot = address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail();
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setType(3); // 商品
        order.setStatus("pending_payment");
        order.setTitle(orderItems.get(0).getProductName() + (orderItems.size() > 1 ? " 等" + orderItems.size() + "件商品" : ""));
        order.setSubtitle("");
        order.setCoverImage(orderItems.get(0).getImage());
        // 运费规则读后台配置（freight.free_threshold / freight.fee），与 C 端确认页同源
        BigDecimal freight = totalAmount.compareTo(BigDecimal.valueOf(configInt("freight.free_threshold", 99))) >= 0
                ? BigDecimal.ZERO : BigDecimal.valueOf(configInt("freight.fee", 10));
        order.setAmount(totalAmount.add(freight));
        order.setRemark("");
        order.setDeleted(0);
        baseMapper.insert(order);

        // 创建商品订单扩展
        OrderProduct orderProduct = new OrderProduct();
        orderProduct.setOrderId(order.getId());
        orderProduct.setAddressId(address.getId());
        orderProduct.setAddressSnapshot(address.getReceiver() + " " + address.getMobile() + " " + addressSnapshot);
        orderProductMapper.insert(orderProduct);

        // 创建订单明细并关联orderId
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        return Result.ok("下单成功", order);
    }

    @Override
    public Result<PageResult<Map<String, Object>>> page(Long userId, String status, Integer type, Integer page, Integer pageSize) {
        Page<Order> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }
        if (status != null && !status.isEmpty()) {
            // C 端标签是聚合口径，库里写作更细的状态：
            // 「待收货」对应 shipped/shipping，「售后」对应退款与取消，
            // 直接 eq 会让这两个标签永远查不到订单。
            if ("after_sale".equals(status)) {
                wrapper.in(Order::getStatus, List.of("refunding", "refunded", "refund_rejected", "cancelled"));
            } else if ("pending_receive".equals(status)) {
                wrapper.in(Order::getStatus, List.of("pending_receive", "shipped", "shipping"));
            } else {
                wrapper.eq(Order::getStatus, status);
            }
        }
        if (type != null) {
            wrapper.eq(Order::getType, type);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> result = baseMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> list = result.getRecords().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("order", order);
            loadExtensionData(map, order);
            return map;
        }).toList();

        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    @Override
    public Result<Map<String, Object>> getDetail(Long id, Long userId) {
        Order order = baseMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该订单");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("order", order);
        loadExtensionData(data, order);
        return Result.ok(data);
    }

    @Override
    @Transactional
    public Result<Void> cancel(Long id, Long userId, String reason) {
        Order order = baseMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }
        // 已完成/已退款/售后中的订单不能再走用户取消，否则会把已核销的库存重复回补
        if (!java.util.Set.of("pending_payment", "pending_use", "pending_delivery").contains(order.getStatus())) {
            throw new BusinessException("当前订单状态不可取消");
        }
        // 采摘订单临近开场不允许线上取消：booking.cancel_hours，0 表示不限制
        if (order.getType() == 1) {
            int hours = configInt("booking.cancel_hours", 0);
            OrderPicking picking = orderPickingMapper.selectOne(
                    new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, id));
            if (hours > 0 && picking != null && picking.getPickDate() != null) {
                LocalDateTime start = picking.getPickDate().atTime(8, 0);
                if (ChronoUnit.HOURS.between(LocalDateTime.now(), start) < hours) {
                    throw new BusinessException("距开场不足 " + hours + " 小时，请联系商家协商取消");
                }
            }
        }
        restoreStock(order);
        order.setStatus("cancelled");
        order.setCancelReason(reason);
        order.setCancelTime(LocalDateTime.now());
        baseMapper.updateById(order);
        return Result.ok("取消成功", null);
    }

    /**
     * 取消/退款时回补库存：场次名额、房态日历、SKU 库存。
     * 下单时已扣减，若不回补名额会被永久占用，等于变相超卖。
     */
    @Override
    @Transactional
    public void restoreStock(Order order) {
        if (order == null || order.getType() == null) {
            return;
        }
        switch (order.getType()) {
            case 1 -> {
                OrderPicking picking = orderPickingMapper.selectOne(
                        new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, order.getId()));
                if (picking != null && picking.getSessionId() != null) {
                    PickingSession session = pickingSessionMapper.selectById(picking.getSessionId());
                    if (session != null) {
                        int back = Math.min(session.getBookedCount(), picking.getPeopleNum() == null ? 0 : picking.getPeopleNum());
                        session.setBookedCount(session.getBookedCount() - back);
                        pickingSessionMapper.updateById(session);
                    }
                }
            }
            case 2 -> {
                OrderHomestay oh = orderHomestayMapper.selectOne(
                        new LambdaQueryWrapper<OrderHomestay>().eq(OrderHomestay::getOrderId, order.getId()));
                if (oh != null && oh.getCheckIn() != null && oh.getCheckOut() != null) {
                    LocalDate d = oh.getCheckIn();
                    while (d.isBefore(oh.getCheckOut())) {
                        RoomCalendar cal = roomCalendarMapper.selectOne(
                                new LambdaQueryWrapper<RoomCalendar>()
                                        .eq(RoomCalendar::getRoomId, oh.getRoomId())
                                        .eq(RoomCalendar::getRoomDate, d));
                        if (cal != null) {
                            cal.setStock(cal.getStock() + 1);
                            roomCalendarMapper.updateById(cal);
                        }
                        d = d.plusDays(1);
                    }
                }
            }
            case 3 -> {
                List<OrderItem> items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
                for (OrderItem item : items) {
                    ProductSku sku = productSkuMapper.selectById(item.getSkuId());
                    if (sku != null) {
                        sku.setStock(sku.getStock() + item.getQuantity());
                        sku.setSales(Math.max(0, sku.getSales() - item.getQuantity()));
                        productSkuMapper.updateById(sku);
                    }
                    Product product = productMapper.selectById(item.getProductId());
                    if (product != null) {
                        product.setSales(Math.max(0, product.getSales() - item.getQuantity()));
                        productMapper.updateById(product);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** 订单完成时写结算单：抽成比例读 sys_config.commission.rate，同一订单只写一次 */
    @Override
    @Transactional
    public void writeSettlement(Order order) {
        if (order == null || order.getMerchantId() == null) {
            return;
        }
        Long exists = settlementMapper.selectCount(
                new LambdaQueryWrapper<Settlement>().eq(Settlement::getOrderId, order.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        BigDecimal rate = BigDecimal.valueOf(0.05);
        try {
            String v = sysConfigService.getValue("commission.rate").getData();
            if (v != null && !v.isBlank()) {
                rate = new BigDecimal(v.trim());
                // 抽成比例只接受 0~1，配错（如填 5）会直接算成 500% 抽成
                if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                    log.warn("commission.rate 配置越界(" + v + "), 已回退默认 0.05");
                    rate = BigDecimal.valueOf(0.05);
                }
            }
        } catch (Exception ignored) {
            /* 配置异常时按默认抽成落单，不能挡住结算 */
        }
        BigDecimal amount = order.getPayAmount() != null ? order.getPayAmount() : order.getAmount();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        BigDecimal commission = amount.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
        Settlement s = new Settlement();
        s.setMerchantId(order.getMerchantId());
        s.setOrderId(order.getId());
        s.setOrderAmount(amount);
        s.setCommissionRate(rate);
        s.setCommissionAmount(commission);
        s.setSettleAmount(amount.subtract(commission));
        s.setStatus(0);
        s.setDeleted(0);
        settlementMapper.insert(s);
    }

    @Override
    public Result<Void> confirmReceive(Long id, Long userId) {
        Order order = baseMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }
        // 商家端发货后状态为 shipping（历史数据/管理端可能写 shipped），两者都允许确认收货
        if (!"shipped".equals(order.getStatus()) && !"shipping".equals(order.getStatus())) {
            throw new BusinessException("订单状态不允许确认收货");
        }
        order.setStatus("completed");
        baseMapper.updateById(order);
        writeSettlement(order);

        // 商品订单更新收货时间
        if (order.getType() == 3) {
            OrderProduct op = orderProductMapper.selectOne(
                    new LambdaQueryWrapper<OrderProduct>().eq(OrderProduct::getOrderId, id));
            if (op != null) {
                op.setReceiveTime(LocalDateTime.now());
                orderProductMapper.updateById(op);
            }
        }
        return Result.ok("确认收货成功", null);
    }

    @Override
    public Result<String> verifyCode(Long orderId, Long userId) {
        Order order = baseMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该订单");
        }
        OrderPicking picking = orderPickingMapper.selectOne(
                new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, orderId));
        if (picking == null) {
            throw new BusinessException("采摘订单不存在");
        }
        return Result.ok(picking.getVerifyCode());
    }

    private void loadExtensionData(Map<String, Object> data, Order order) {
        switch (order.getType()) {
            case 1 -> {
                OrderPicking picking = orderPickingMapper.selectOne(
                        new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, order.getId()));
                data.put("picking", picking);
            }
            case 2 -> {
                OrderHomestay homestay = orderHomestayMapper.selectOne(
                        new LambdaQueryWrapper<OrderHomestay>().eq(OrderHomestay::getOrderId, order.getId()));
                data.put("homestay", homestay);
            }
            case 3 -> {
                OrderProduct product = orderProductMapper.selectOne(
                        new LambdaQueryWrapper<OrderProduct>().eq(OrderProduct::getOrderId, order.getId()));
                data.put("product", product);
                data.put("items", orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())));
                List<OrderItem> items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
                data.put("items", items);
            }
        }
    }

    @Override
    @Transactional
    public void reverseSettlement(Order order) {
        if (order == null) {
            return;
        }
        Settlement s = settlementMapper.selectOne(new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getOrderId, order.getId()));
        if (s == null) {
            return;
        }
        // 已提现的结算不在这里回滚（涉及资金流水），只冲销未结算部分
        if (s.getStatus() != null && s.getStatus() == 1) {
            log.warn("订单 " + order.getOrderNo() + " 的结算已提现，退款需线下追回");
            return;
        }
        s.setStatus(1);
        s.setSettleAmount(BigDecimal.ZERO);
        s.setCommissionAmount(BigDecimal.ZERO);
        settlementMapper.updateById(s);
    }
}

package com.qingmeili.controller.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.*;
import com.qingmeili.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 商户端 - 订单管理
 *
 * 说明：列表返回时会把子表（采摘/民宿/商品）的关键字段拍平到 map 中，
 * 便于商家端 H5 直接渲染，无需再发起多次详情请求。
 */
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantOrderController {

    private final OrderMapper orderMapper;
    private final OrderPickingMapper orderPickingMapper;
    private final OrderHomestayMapper orderHomestayMapper;
    private final OrderProductMapper orderProductMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;
    private final SettlementMapper settlementMapper;
    private final com.qingmeili.service.OrderService orderService;
    private final com.qingmeili.service.UserMessageService userMessageService;

    /** 订单类型：1=采摘 2=民宿 3=商品 */
    @GetMapping("/picking/orders")
    public Result<PageResult<Map<String, Object>>> pickingOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return getOrders(1, status, page, pageSize);
    }

    @GetMapping("/homestay/orders")
    public Result<PageResult<Map<String, Object>>> homestayOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return getOrders(2, status, page, pageSize);
    }

    @GetMapping("/product/orders")
    public Result<PageResult<Map<String, Object>>> productOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return getOrders(3, status, page, pageSize);
    }

    /** 订单详情（含子表信息） */
    @GetMapping("/orders/{id}")
    public Result<Map<String, Object>> getById(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("订单不存在");
        }
        return Result.ok(enrich(order));
    }

    /**
     * 按核销码查询订单（不修改状态）
     * 用于商家端「扫码核销」的查询步骤。
     */
    @GetMapping("/picking/orders/verify-query")
    public Result<Map<String, Object>> queryByVerifyCode(@RequestParam String code) {
        OrderPicking picking = orderPickingMapper.selectOne(
                new LambdaQueryWrapper<OrderPicking>()
                        .eq(OrderPicking::getVerifyCode, code)
                        .last("LIMIT 1"));
        if (picking == null) {
            return Result.fail("核销码无效");
        }
        Order order = orderMapper.selectById(picking.getOrderId());
        if (order == null) {
            return Result.fail("核销码无效");
        }
        // 非本商户订单也返回，由核销动作做归属校验（前端可展示原因）
        return Result.ok(enrich(order));
    }

    /**
     * 按核销码核销采摘订单（完成全部业务判断）
     * 校验：码存在 / 未核销 / 属于当前商户 / 状态可核销
     */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/picking/orders/verify-by-code")
    @OperationLog(module = "商户订单", action = "核销采摘订单")
    public Result<Map<String, Object>> verifyByCode(@RequestParam String code) {
        OrderPicking picking = orderPickingMapper.selectOne(
                new LambdaQueryWrapper<OrderPicking>()
                        .eq(OrderPicking::getVerifyCode, code)
                        .last("LIMIT 1"));
        if (picking == null) {
            return Result.fail("核销码无效");
        }
        Order order = orderMapper.selectById(picking.getOrderId());
        if (order == null) {
            return Result.fail("核销码无效");
        }
        if (!Objects.equals(order.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("无权核销该订单");
        }
        if ("completed".equals(order.getStatus())) {
            return Result.fail("该订单已经核销");
        }
        if (!"pending_use".equals(order.getStatus())) {
            return Result.fail("该订单当前状态不可核销");
        }
        order.setStatus("completed");
        orderMapper.updateById(order);
        picking.setVerifyTime(LocalDateTime.now());
        orderPickingMapper.updateById(picking);
        orderService.writeSettlement(order);
        userMessageService.notifyOrderEvent(order, "verified");
        return Result.ok("核销成功", enrich(order));
    }

    /** 核销（按订单 ID，保留原接口） */
    @PostMapping("/picking/orders/{id}/verify")
    @OperationLog(module = "商户订单", action = "核销采摘订单")
    public Result<Void> verifyPickingOrder(@PathVariable Long id, @RequestParam String code) {
        Order order = orderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("订单不存在");
        }
        if (!"pending_use".equals(order.getStatus())) {
            return Result.fail("订单状态不允许核销");
        }
        OrderPicking picking = orderPickingMapper.selectOne(
                new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, id));
        if (picking == null || picking.getVerifyCode() == null
                || code == null || !code.trim().equals(picking.getVerifyCode())) {
            return Result.fail("核销码错误");
        }
        order.setStatus("completed");
        orderMapper.updateById(order);
        picking.setVerifyTime(LocalDateTime.now());
        orderPickingMapper.updateById(picking);
        orderService.writeSettlement(order);
        userMessageService.notifyOrderEvent(order, "verified");
        return Result.ok("核销成功", null);
    }

    /** 商品订单发货 */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/orders/{id}/ship")
    @OperationLog(module = "商户订单", action = "商品订单发货")
    public Result<Map<String, Object>> ship(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        Order order = orderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("订单不存在");
        }
        if (!"pending_delivery".equals(order.getStatus())) {
            return Result.fail("该订单当前状态不可发货");
        }
        String company = body != null ? body.get("company") : null;
        String trackingNo = body != null ? body.get("trackingNo") : null;

        OrderProduct op = orderProductMapper.selectOne(
                new LambdaQueryWrapper<OrderProduct>().eq(OrderProduct::getOrderId, id));
        if (op == null) {
            op = new OrderProduct();
            op.setOrderId(id);
            op.setLogisticsCompany(company);
            op.setLogisticsNo(trackingNo);
            op.setShipTime(LocalDateTime.now());
            op.setCreateTime(LocalDateTime.now());
            op.setUpdateTime(LocalDateTime.now());
            orderProductMapper.insert(op);
        } else {
            op.setLogisticsCompany(company);
            op.setLogisticsNo(trackingNo);
            op.setShipTime(LocalDateTime.now());
            orderProductMapper.updateById(op);
        }
        order.setStatus("shipped");
        orderMapper.updateById(order);
        userMessageService.notifyOrderEvent(order, "shipped");
        return Result.ok("发货成功", enrich(order));
    }

    /** 拒绝预约：未核销的采摘订单，商家可说明原因取消并全额退款，名额同步释放 */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/orders/{id}/reject-reservation")
    @OperationLog(module = "商户订单", action = "拒绝预约")
    public Result<Map<String, Object>> rejectReservation(@PathVariable Long id,
                                                         @RequestBody(required = false) Map<String, String> body) {
        Order order = orderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("订单不存在");
        }
        if (order.getType() == null || order.getType() != 1 || !"pending_use".equals(order.getStatus())) {
            return Result.fail("仅待核销的采摘预约可拒绝");
        }
        String reason = body != null && body.get("reason") != null ? body.get("reason").trim() : "商家临时无法接待";
        orderService.restoreStock(order);
        orderService.reverseSettlement(order);
        order.setStatus("refunded");
        order.setRefundAmount(order.getPayAmount() != null ? order.getPayAmount() : order.getAmount());
        order.setRefundTime(LocalDateTime.now());
        order.setCancelReason(reason);
        orderMapper.updateById(order);
        userMessageService.notifyOrderEvent(order, "rejected_by_merchant");
        return Result.ok("已拒绝预约并全额退款", enrich(order));
    }

    /** 同意退款 */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/orders/{id}/refund/approve")
    @OperationLog(module = "商户订单", action = "同意退款")
    public Result<Map<String, Object>> refundApprove(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("订单不存在");
        }
        if (!"refunding".equals(order.getStatus())) {
            return Result.fail("该订单当前状态不可退款");
        }
        orderService.restoreStock(order);
        orderService.reverseSettlement(order);
        order.setStatus("refunded");
        order.setRefundAmount(order.getPayAmount() != null ? order.getPayAmount() : order.getAmount());
        order.setRefundTime(LocalDateTime.now());
        orderMapper.updateById(order);
        userMessageService.notifyOrderEvent(order, "refunded");
        return Result.ok("已同意退款", enrich(order));
    }

    /** 拒绝退款 */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/orders/{id}/refund/reject")
    @OperationLog(module = "商户订单", action = "拒绝退款")
    public Result<Map<String, Object>> refundReject(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, String> body) {
        Order order = orderMapper.selectById(id);
        if (order == null || !Objects.equals(order.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("订单不存在");
        }
        if (!"refunding".equals(order.getStatus())) {
            return Result.fail("该订单当前状态不可操作");
        }
        String reason = body != null ? body.get("reason") : null;
        // 拒绝退款订单回到已完成，但退款单状态必须留痕为 refund_rejected，
        // 此前直接写 completed 导致游客端「退款被拒」态永不可达
        order.setStatus("refund_rejected");
        order.setRemark(reason != null ? reason : "商户拒绝退款");
        orderMapper.updateById(order);
        userMessageService.notifyOrderEvent(order, "refund_rejected");
        return Result.ok("已拒绝退款", enrich(order));
    }

    // ==================== 内部方法 ====================

    private Result<PageResult<Map<String, Object>>> getOrders(Integer type, String status, Integer page, Integer pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getMerchantId, UserContext.getMerchantId())
               .eq(Order::getType, type);
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> pageObj = new Page<>(page, pageSize);
        Page<Order> result = orderMapper.selectPage(pageObj, wrapper);
        List<Map<String, Object>> list = result.getRecords().stream().map(this::enrich).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** 把订单子表信息拍平进 map，供前端直接渲染 */
    private Map<String, Object> enrich(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.getId());
        map.put("orderNo", order.getOrderNo());
        map.put("userId", order.getUserId());
        map.put("merchantId", order.getMerchantId());
        map.put("type", order.getType());
        map.put("status", order.getStatus());
        map.put("title", order.getTitle());
        map.put("subtitle", order.getSubtitle());
        map.put("coverImage", order.getCoverImage());
        map.put("amount", order.getAmount());
        map.put("payAmount", order.getPayAmount());
        map.put("payTime", order.getPayTime());
        map.put("refundAmount", order.getRefundAmount());
        map.put("refundTime", order.getRefundTime());
        map.put("refundNo", order.getRefundNo());
        map.put("refundReason", order.getRefundReason());
        map.put("remark", order.getRemark());
        map.put("createTime", order.getCreateTime());

        // 下单用户信息
        User user = order.getUserId() != null ? userMapper.selectById(order.getUserId()) : null;
        if (user != null) {
            map.put("customerName", user.getNickname());
            map.put("customerMobile", com.qingmeili.common.util.MaskSerializers.mobile(user.getMobile()));
        }

        Long oid = order.getId();
        if (Integer.valueOf(1).equals(order.getType())) {
            OrderPicking p = orderPickingMapper.selectOne(
                    new LambdaQueryWrapper<OrderPicking>().eq(OrderPicking::getOrderId, oid));
            if (p != null) {
                map.put("sessionId", p.getSessionId());
                map.put("orchardId", p.getOrchardId());
                map.put("peopleNum", p.getPeopleNum());
                map.put("contactName", p.getContactName());
                map.put("contactMobile", p.getContactMobile());
                map.put("verifyCode", p.getVerifyCode());
                map.put("verifyTime", p.getVerifyTime());
                map.put("pickDate", p.getPickDate());
                map.put("customerName", p.getContactName());
                map.put("customerMobile", com.qingmeili.common.util.MaskSerializers.mobile(p.getContactMobile()));
            }
        } else if (Integer.valueOf(2).equals(order.getType())) {
            OrderHomestay h = orderHomestayMapper.selectOne(
                    new LambdaQueryWrapper<OrderHomestay>().eq(OrderHomestay::getOrderId, oid));
            if (h != null) {
                map.put("roomId", h.getRoomId());
                map.put("homestayId", h.getHomestayId());
                map.put("checkIn", h.getCheckIn());
                map.put("checkOut", h.getCheckOut());
                map.put("nights", h.getNights());
                map.put("guestName", h.getGuestName());
                map.put("guestMobile", h.getGuestMobile());
                map.put("customerName", h.getGuestName());
                map.put("customerMobile", com.qingmeili.common.util.MaskSerializers.mobile(h.getGuestMobile()));
                map.put("roomName", order.getTitle());
            }
        } else if (Integer.valueOf(3).equals(order.getType())) {
            OrderProduct op = orderProductMapper.selectOne(
                    new LambdaQueryWrapper<OrderProduct>().eq(OrderProduct::getOrderId, oid));
            if (op != null) {
                map.put("addressSnapshot", op.getAddressSnapshot());
                map.put("logisticsNo", op.getLogisticsNo());
                map.put("logisticsCompany", op.getLogisticsCompany());
                map.put("shipTime", op.getShipTime());
                map.put("receiveTime", op.getReceiveTime());
                map.put("address", op.getAddressSnapshot());
            }
            // 商品明细
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, oid));
            if (items != null && !items.isEmpty()) {
                map.put("items", items);
            }
        }
        return map;
    }
}

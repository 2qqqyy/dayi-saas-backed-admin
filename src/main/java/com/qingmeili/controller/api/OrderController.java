package com.qingmeili.controller.api;

import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.order.HomestayOrderDTO;
import com.qingmeili.dto.order.PickingOrderDTO;
import com.qingmeili.dto.order.ProductOrderDTO;
import com.qingmeili.entity.Order;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.service.OrderService;
import com.qingmeili.service.SysConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * C端 - 订单
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final SysConfigService sysConfigService;
    private final com.qingmeili.service.ReviewService reviewService;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return orderService.page(UserContext.getUserId(), status, type, page, pageSize);
    }

    @GetMapping("/{id}")
  public Result<Map<String, Object>> getById(@PathVariable Long id) {
      return orderService.getDetail(id, UserContext.getUserId());
  }

    @PostMapping("/picking")
    public Result<Order> createPickingOrder(@Valid @RequestBody PickingOrderDTO dto) {
        sysConfigService.assertGuestWritesOpen();
        return orderService.createPickingOrder(UserContext.getUserId(), dto);
    }

    @PostMapping("/homestay")
    public Result<Order> createHomestayOrder(@Valid @RequestBody HomestayOrderDTO dto) {
        sysConfigService.assertGuestWritesOpen();
        return orderService.createHomestayOrder(UserContext.getUserId(), dto);
    }

    @PostMapping("/product")
    public Result<Order> createProductOrder(@Valid @RequestBody ProductOrderDTO dto) {
        sysConfigService.assertGuestWritesOpen();
        return orderService.createProductOrder(UserContext.getUserId(), dto);
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return orderService.cancel(id, UserContext.getUserId(), reason);
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirmReceive(@PathVariable Long id) {
        return orderService.confirmReceive(id, UserContext.getUserId());
    }

    /**
     * C端申请退款：POST /api/order/{id}/refund  body: { reason }
     * 仅本人订单、且处于已支付未完结退款流程的状态可申请，置为 refunding 等待商户/管理员处理
     */
    @PostMapping("/{id}/refund")
    public Result<Void> refund(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("无权操作该订单");
        }
        // 已支付且未处于退款/已退款/已取消的状态才可申请（shipped 为历史/管理端写入的配送中）
        if (!java.util.Set.of("pending_delivery", "pending_use", "completed",
                "shipping", "shipped", "pending_receive", "refund_rejected").contains(order.getStatus())) {
            throw new BusinessException("当前状态不可申请退款");
        }
        String reason = body != null ? body.get("reason") : null;
        order.setStatus("refunding");
        order.setRefundReason(reason);
        order.setRefundNo("RF" + order.getOrderNo());
        order.setRefundTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return Result.ok("退款申请已提交", null);
    }

    @GetMapping("/{id}/verify-code")
   public Result<String> verifyCode(@PathVariable Long id) {
       return orderService.verifyCode(id, UserContext.getUserId());
   }

    /** 订单评价：三类订单通用，完成后可评，一单一评 */
    @PostMapping("/{id}/review")
    public Result<com.qingmeili.entity.Review> review(@PathVariable Long id,
                                                      @Valid @RequestBody com.qingmeili.dto.review.ReviewDTO dto) {
        return reviewService.create(id, UserContext.getUserId(), dto);
    }

    /** 我的评价列表：订单页展示"已评价"态与内容 */
    @GetMapping("/reviews")
    public Result<com.qingmeili.common.result.PageResult<com.qingmeili.entity.Review>> myReviews(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return reviewService.listByUser(UserContext.getUserId(), page, pageSize);
    }
}

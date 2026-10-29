package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.order.HomestayOrderDTO;
import com.qingmeili.dto.order.PickingOrderDTO;
import com.qingmeili.dto.order.ProductOrderDTO;
import com.qingmeili.entity.Order;

import java.util.Map;

/**
 * 订单服务
 */
public interface OrderService extends IService<Order> {

    Result<Order> createPickingOrder(Long userId, PickingOrderDTO dto);

    Result<Order> createHomestayOrder(Long userId, HomestayOrderDTO dto);

    Result<Order> createProductOrder(Long userId, ProductOrderDTO dto);

    Result<PageResult<Map<String, Object>>> page(Long userId, String status, Integer type, Integer page, Integer pageSize);

    Result<Map<String, Object>> getDetail(Long id, Long userId);

    Result<Void> cancel(Long id, Long userId, String reason);

    Result<Void> confirmReceive(Long id, Long userId);

    Result<String> verifyCode(Long orderId, Long userId);

    /** 取消/退款时回补场次名额、房态库存、SKU 库存 */
    void restoreStock(Order order);

    /** 订单完成时按 sys_config 抽成比例写结算单（幂等） */
    void writeSettlement(Order order);

    /** 退款时冲销未提现的结算单，防止退款后仍可提现 */
    void reverseSettlement(Order order);
}

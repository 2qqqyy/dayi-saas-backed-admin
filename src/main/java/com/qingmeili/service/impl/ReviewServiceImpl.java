package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.review.ReviewDTO;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.OrderHomestay;
import com.qingmeili.entity.OrderItem;
import com.qingmeili.entity.Review;
import com.qingmeili.mapper.OrderHomestayMapper;
import com.qingmeili.mapper.OrderItemMapper;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.ReviewMapper;
import com.qingmeili.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价服务实现
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    private final OrderMapper orderMapper;
    private final OrderHomestayMapper orderHomestayMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public Result<Review> create(Long orderId, Long userId, ReviewDTO dto) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权评价该订单");
        }
        // 检查是否已评价
        Long count = baseMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderId, orderId));
        if (count > 0) {
            throw new BusinessException("该订单已评价");
        }
        Review review = new Review();
        review.setOrderId(orderId);
        review.setUserId(userId);
        review.setMerchantId(order.getMerchantId());
        review.setType(order.getType());
        review.setScore(dto.getScore());
        review.setContent(dto.getContent());
        review.setImages(dto.getImages());
        review.setStatus(1);
        baseMapper.insert(review);
        return Result.ok("评价成功", review);
    }

    @Override
    public Result<PageResult<Review>> listByMerchant(Long merchantId, Integer page, Integer pageSize) {
        Page<Review> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getMerchantId, merchantId);
        wrapper.orderByDesc(Review::getCreateTime);
        Page<Review> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<PageResult<Review>> listByHomestay(Long homestayId, Integer page, Integer pageSize) {
        List<Long> orderIds = orderHomestayMapper.selectList(
                        new LambdaQueryWrapper<OrderHomestay>().eq(OrderHomestay::getHomestayId, homestayId))
                .stream().map(OrderHomestay::getOrderId).toList();
        Page<Review> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getType, 2);
        if (orderIds.isEmpty()) {
            wrapper.eq(Review::getId, -1L);
        } else {
            wrapper.in(Review::getOrderId, orderIds);
        }
        wrapper.orderByDesc(Review::getCreateTime);
        Page<Review> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<PageResult<Review>> listByProduct(Long productId, Integer page, Integer pageSize) {
        // review 表没有 product_id，商品维度只能按 order_item.product_id 反查订单
        List<Long> orderIds = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getProductId, productId))
                .stream().map(OrderItem::getOrderId).distinct().toList();
        Page<Review> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getType, 3);
        if (orderIds.isEmpty()) {
            wrapper.eq(Review::getId, -1L);
        } else {
            wrapper.in(Review::getOrderId, orderIds);
        }
        wrapper.orderByDesc(Review::getCreateTime);
        Page<Review> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<Void> reply(Long reviewId, String reply) {
        Review review = baseMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        review.setReply(reply);
        review.setReplyTime(LocalDateTime.now());
        baseMapper.updateById(review);
        return Result.ok("回复成功", null);
    }

    @Override
    public Result<PageResult<Review>> listByUser(Long userId, Integer page, Integer pageSize) {
        Page<Review> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreateTime);
        Page<Review> result = baseMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }
}

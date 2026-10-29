package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.review.ReviewDTO;
import com.qingmeili.entity.Review;

/**
 * 评价服务
 */
public interface ReviewService extends IService<Review> {

    Result<Review> create(Long orderId, Long userId, ReviewDTO dto);

    Result<PageResult<Review>> listByMerchant(Long merchantId, Integer page, Integer pageSize);

    Result<PageResult<Review>> listByHomestay(Long homestayId, Integer page, Integer pageSize);

    Result<PageResult<Review>> listByProduct(Long productId, Integer page, Integer pageSize);

    Result<Void> reply(Long reviewId, String reply);

    Result<PageResult<Review>> listByUser(Long userId, Integer page, Integer pageSize);
}

package com.qingmeili.controller.api;

import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Homestay;
import com.qingmeili.entity.Review;
import com.qingmeili.dto.review.ReviewDTO;
import com.qingmeili.service.HomestayService;
import com.qingmeili.service.ReviewService;
import com.qingmeili.common.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * C端 - 民宿（公开接口）
 */
@RestController
@RequestMapping("/api/homestay")
@RequiredArgsConstructor
public class HomestayController {

    private final HomestayService homestayService;
    private final ReviewService reviewService;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return homestayService.page(page, pageSize, keyword);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return homestayService.getDetail(id);
    }

    @PostMapping("/orders/{id}/review")
    public Result<Review> createReview(@PathVariable Long id, @Valid @RequestBody ReviewDTO dto) {
        return reviewService.create(id, UserContext.getUserId(), dto);
    }

    @GetMapping("/{id}/reviews")
    public Result<com.qingmeili.common.result.PageResult<Review>> listReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return reviewService.listByHomestay(id, page, pageSize);
    }
}

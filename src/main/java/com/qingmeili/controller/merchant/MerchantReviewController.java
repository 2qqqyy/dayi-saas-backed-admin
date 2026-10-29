package com.qingmeili.controller.merchant;

import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Review;
import com.qingmeili.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商户端 - 游客评价查看与回复
 */
@RestController
@RequestMapping("/api/merchant/reviews")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public Result<PageResult<Review>> list(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return reviewService.listByMerchant(UserContext.getMerchantId(), page, pageSize);
    }

    @PostMapping("/{id}/reply")
    @OperationLog(module = "商户评价", action = "回复评价")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reply = body == null ? null : body.get("reply");
        if (reply == null || reply.isBlank()) {
            return Result.fail("回复内容不能为空");
        }
        return reviewService.reply(id, reply.trim());
    }
}

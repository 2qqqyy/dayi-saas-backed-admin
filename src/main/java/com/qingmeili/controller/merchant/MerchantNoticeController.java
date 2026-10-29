package com.qingmeili.controller.merchant;

import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商户端 - 村务公告（带已读状态）
 */
@RestController
@RequestMapping("/api/merchant/notices")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantNoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(noticeService.ownerFeed(NoticeService.OWNER_MERCHANT, UserContext.getMerchantId(), limit));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.ok(noticeService.unreadCount(NoticeService.OWNER_MERCHANT, UserContext.getMerchantId()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(noticeService.ownerDetail(NoticeService.OWNER_MERCHANT, UserContext.getMerchantId(), id));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        noticeService.markRead(NoticeService.OWNER_MERCHANT, UserContext.getMerchantId(), id);
        return Result.ok("已标记为已读", null);
    }
}

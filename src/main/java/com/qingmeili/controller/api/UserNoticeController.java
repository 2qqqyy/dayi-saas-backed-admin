package com.qingmeili.controller.api;

import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C端 - 公告（登录后视角，带已读状态）
 */
@RestController
@RequestMapping("/api/user/notices")
@RequiredArgsConstructor
public class UserNoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(noticeService.ownerFeed(NoticeService.OWNER_USER, UserContext.getUserId(), limit));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(noticeService.ownerDetail(NoticeService.OWNER_USER, UserContext.getUserId(), id));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        noticeService.markRead(NoticeService.OWNER_USER, UserContext.getUserId(), id);
        return Result.ok("已标记为已读", null);
    }
}

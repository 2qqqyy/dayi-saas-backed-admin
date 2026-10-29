package com.qingmeili.controller.api;

import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.SupportTicket;
import com.qingmeili.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 游客端 - 客服 / 售后 / 纠纷工单
 */
@RestController
@RequestMapping("/api/user/tickets")
@RequiredArgsConstructor
@RequireRole({"tourist"})
public class UserTicketController {

    private final SupportTicketService ticketService;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return ticketService.listForUser(UserContext.getUserId(), page, pageSize);
    }

    @PostMapping
    public Result<SupportTicket> create(@RequestBody Map<String, Object> body) {
        Long orderId = body.get("orderId") == null ? null : Long.valueOf(String.valueOf(body.get("orderId")));
        return ticketService.create(UserContext.getUserId(), orderId,
                str(body.get("category")), str(body.get("title")), str(body.get("content")));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Result<Map<String, Object>> res = ticketService.detail(id);
        Map<String, Object> data = res.getData();
        SupportTicket t = (SupportTicket) data.get("ticket");
        if (t == null || !UserContext.getUserId().equals(t.getUserId())) {
            return Result.fail("无权查看该工单");
        }
        return res;
    }

    @PostMapping("/{id}/messages")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ticketService.reply(id, "USER", UserContext.getUserId(), "游客", body.get("content"));
    }

    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        // 游客关闭：仅本人可操作，服务层会校验归属
        Result<Void> check = ticketService.updateStatus(id, "USER", UserContext.getUserId(), 3, false);
        return check;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}

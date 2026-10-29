package com.qingmeili.controller.admin;

import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 平台 - 售后/纠纷工单仲裁：可回复、升级、结案
 */
@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminTicketController {

    private final SupportTicketService ticketService;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return ticketService.listForAdmin(status, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return ticketService.detail(id);
    }

    @PostMapping("/{id}/messages")
    @OperationLog(module = "售后工单", action = "平台回复工单")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ticketService.reply(id, "ADMIN", UserContext.getUserId(), "平台客服", body.get("content"));
    }

    @PutMapping("/{id}/status")
    @OperationLog(module = "售后工单", action = "平台处理工单")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int status = Integer.parseInt(String.valueOf(body.getOrDefault("status", 2)));
        boolean escalate = body.get("escalate") != null && Boolean.parseBoolean(String.valueOf(body.get("escalate")));
        return ticketService.updateStatus(id, "ADMIN", UserContext.getUserId(), status, escalate);
    }
}

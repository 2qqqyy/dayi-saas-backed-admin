package com.qingmeili.controller.merchant;

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
 * 商家端 - 售后 / 纠纷工单处理
 */
@RestController
@RequestMapping("/api/merchant/tickets")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantTicketController {

    private final SupportTicketService ticketService;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return ticketService.listForMerchant(UserContext.getMerchantId(), status, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Result<Map<String, Object>> res = ticketService.detail(id);
        Object t = res.getData() == null ? null : res.getData().get("ticket");
        if (!(t instanceof com.qingmeili.entity.SupportTicket ticket)
                || !java.util.Objects.equals(ticket.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("无权查看该工单");
        }
        if (ticket.getPriority() != null && ticket.getPriority() == 1) {
            return Result.fail("该工单已升级平台处理");
        }
        return res;
    }

    @PostMapping("/{id}/messages")
    @OperationLog(module = "售后工单", action = "回复工单")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Result<Void> check = assertHandledByMerchant(id);
        if (check.getCode() != 0) {
            return check;
        }
        return ticketService.reply(id, "MERCHANT", UserContext.getMerchantId(), "商家", body.get("content"));
    }

    @PutMapping("/{id}/status")
    @OperationLog(module = "售后工单", action = "更新工单状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Result<Void> check = assertHandledByMerchant(id);
        if (check.getCode() != 0) {
            return check;
        }
        Object raw = body.get("status");
        if (raw == null) {
            return Result.fail("缺少状态参数");
        }
        int status;
        try {
            status = Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return Result.fail("状态不合法");
        }
        return ticketService.updateStatus(id, "MERCHANT", UserContext.getMerchantId(), status, false);
    }

    /** 商家只能操作本店且未被平台升级的工单 */
    private Result<Void> assertHandledByMerchant(Long id) {
        Result<Map<String, Object>> res = ticketService.detail(id);
        Object raw = res.getData() == null ? null : res.getData().get("ticket");
        if (!(raw instanceof com.qingmeili.entity.SupportTicket ticket)
                || !java.util.Objects.equals(ticket.getMerchantId(), UserContext.getMerchantId())) {
            return Result.fail("无权操作该工单");
        }
        if (ticket.getPriority() != null && ticket.getPriority() == 1) {
            return Result.fail("该工单已升级平台处理");
        }
        return Result.ok("ok", null);
    }
}

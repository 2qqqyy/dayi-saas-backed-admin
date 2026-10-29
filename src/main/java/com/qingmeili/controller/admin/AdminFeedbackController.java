package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Admin;
import com.qingmeili.entity.UserFeedback;
import com.qingmeili.mapper.AdminMapper;
import com.qingmeili.mapper.UserFeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理 - 用户反馈处理（数据来自 C 端「意见反馈」提交）
 */
@RestController
@RequestMapping("/api/admin/feedbacks")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminFeedbackController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserFeedbackMapper userFeedbackMapper;
    private final AdminMapper adminMapper;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        LambdaQueryWrapper<UserFeedback> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(UserFeedback::getStatus, status);
        if (StringUtils.hasText(type)) wrapper.eq(UserFeedback::getType, type);
        if (StringUtils.hasText(keyword)) wrapper.like(UserFeedback::getContent, keyword);
        wrapper.orderByDesc(UserFeedback::getCreateTime);
        Page<UserFeedback> result = userFeedbackMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<Map<String, Object>> list = result.getRecords().stream().map(this::toMap).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    /** 回复并标记已处理 */
    @PutMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reply = body.get("reply");
        if (!StringUtils.hasText(reply)) throw new BusinessException("回复内容不能为空");
        UserFeedback feedback = require(id);
        feedback.setReply(reply.trim());
        feedback.setStatus(1);
        feedback.setHandler(currentAdminName());
        feedback.setHandleTime(LocalDateTime.now());
        userFeedbackMapper.updateById(feedback);
        return Result.ok("已回复", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        UserFeedback feedback = require(id);
        feedback.setStatus(status);
        feedback.setHandler(currentAdminName());
        feedback.setHandleTime(LocalDateTime.now());
        userFeedbackMapper.updateById(feedback);
        return Result.ok("状态已更新", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        require(id);
        userFeedbackMapper.deleteById(id);
        return Result.ok("已删除", null);
    }

    private UserFeedback require(Long id) {
        UserFeedback feedback = userFeedbackMapper.selectById(id);
        if (feedback == null) throw new BusinessException("反馈不存在");
        return feedback;
    }

    private String currentAdminName() {
        Long adminId = UserContext.getUserId();
        if (adminId == null) return "管理员";
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) return "管理员";
        return StringUtils.hasText(admin.getRealName()) ? admin.getRealName() : admin.getUsername();
    }

    private Map<String, Object> toMap(UserFeedback f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("userId", f.getUserId());
        m.put("user", StringUtils.hasText(f.getUserName()) ? f.getUserName() : "游客");
        m.put("type", nz(f.getType()));
        m.put("content", nz(f.getContent()));
        m.put("contact", nz(f.getContact()));
        m.put("status", f.getStatus() == null ? 0 : f.getStatus());
        m.put("reply", nz(f.getReply()));
        m.put("handler", nz(f.getHandler()));
        m.put("handleTime", f.getHandleTime() == null ? "" : f.getHandleTime().format(TIME));
        m.put("time", f.getCreateTime() == null ? "" : f.getCreateTime().format(TIME));
        return m;
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}

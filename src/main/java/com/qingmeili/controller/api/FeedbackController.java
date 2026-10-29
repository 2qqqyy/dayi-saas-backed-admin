package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.User;
import com.qingmeili.entity.UserFeedback;
import com.qingmeili.mapper.UserFeedbackMapper;
import com.qingmeili.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C端 - 意见反馈（提交后进入后台「用户反馈」处理）
 */
@RestController
@RequestMapping("/api/user/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> TYPES = List.of("功能建议", "订单问题", "内容纠错", "其他");

    private final UserFeedbackMapper userFeedbackMapper;
    private final UserMapper userMapper;

    @GetMapping("/types")
    public Result<List<String>> types() {
        return Result.ok(TYPES);
    }

    @PostMapping
    public Result<Map<String, Object>> submit(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        String content = body.get("content");
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("请填写反馈内容");
        }
        if (content.trim().length() > 1000) {
            throw new BusinessException("反馈内容不能超过 1000 字");
        }
        User user = userMapper.selectById(userId);

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setUserName(user != null && StringUtils.hasText(user.getNickname()) ? user.getNickname() : "青梅用户");
        feedback.setType(TYPES.contains(body.get("type")) ? body.get("type") : "其他");
        feedback.setContent(content.trim());
        feedback.setContact(body.get("contact"));
        feedback.setStatus(0);
        userFeedbackMapper.insert(feedback);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", feedback.getId());
        data.put("status", feedback.getStatus());
        return Result.ok("反馈已提交", data);
    }

    @GetMapping
    public Result<List<Map<String, Object>>> mine() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        List<Map<String, Object>> list = userFeedbackMapper.selectList(new LambdaQueryWrapper<UserFeedback>()
                        .eq(UserFeedback::getUserId, userId)
                        .orderByDesc(UserFeedback::getCreateTime)).stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("type", nz(f.getType()));
                    m.put("content", nz(f.getContent()));
                    m.put("status", f.getStatus() == null ? 0 : f.getStatus());
                    m.put("reply", nz(f.getReply()));
                    m.put("handler", nz(f.getHandler()));
                    m.put("time", f.getCreateTime() == null ? "" : f.getCreateTime().format(TIME));
                    return m;
                }).toList();
        return Result.ok(list);
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}

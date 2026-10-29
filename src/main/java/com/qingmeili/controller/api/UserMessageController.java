package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.UserMessage;
import com.qingmeili.mapper.UserMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 游客端 - 站内消息（订单事件通知）
 */
@RestController
@RequestMapping("/api/user/messages")
@RequiredArgsConstructor
@RequireRole({"tourist"})
public class UserMessageController {

    private final UserMessageMapper userMessageMapper;

    @GetMapping
    public Result<List<UserMessage>> list(@RequestParam(defaultValue = "30") int limit) {
        List<UserMessage> list = userMessageMapper.selectList(
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getUserId, UserContext.getUserId())
                        .orderByDesc(UserMessage::getCreateTime)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        return Result.ok(list);
    }

    @GetMapping("/unread-count")
    public Result<Integer> unread() {
        Long n = userMessageMapper.selectCount(
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getUserId, UserContext.getUserId())
                        .eq(UserMessage::getReadFlag, 0));
        return Result.ok(n == null ? 0 : n.intValue());
    }

    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        UserMessage m = userMessageMapper.selectById(id);
        if (m == null || !m.getUserId().equals(UserContext.getUserId())) {
            return Result.fail("消息不存在");
        }
        m.setReadFlag(1);
        userMessageMapper.updateById(m);
        return Result.ok("已读", null);
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        List<UserMessage> list = userMessageMapper.selectList(
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getUserId, UserContext.getUserId())
                        .eq(UserMessage::getReadFlag, 0));
        for (UserMessage m : list) {
            m.setReadFlag(1);
            userMessageMapper.updateById(m);
        }
        return Result.ok("已全部已读", null);
    }
}

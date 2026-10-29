package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.qingmeili.common.result.Result;
import com.qingmeili.common.util.JwtUtil;
import com.qingmeili.entity.AiConversation;
import com.qingmeili.entity.User;
import com.qingmeili.mapper.AiConversationMapper;
import com.qingmeili.mapper.UserMapper;
import com.qingmeili.service.AiService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C端 - AI 山野向导（公开接口，问答与行程全部走库内真实数据）。
 * 路径不经过 JwtInterceptor，这里按需解析 token，登录用户的问答会带上身份留痕。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final AiConversationMapper aiConversationMapper;
    private final com.qingmeili.mapper.AiItineraryMapper aiItineraryMapper;

    @PostMapping("/chat")
    public Result<JsonNode> chat(@RequestBody Map<String, Object> body,
                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        Identity who = resolve(authorization);
        return aiService.chat(text(body.get("sessionId")), text(body.get("message")), who.userId(), who.userName());
    }

    @PostMapping("/itinerary")
    public Result<JsonNode> itinerary(@RequestBody Map<String, Object> body,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        Identity who = resolve(authorization);
        return aiService.itinerary(text(body.get("date")), integer(body.get("people")), text(body.get("type")),
                who.userId(), who.userName());
    }

    @PostMapping("/itinerary/{id}/save")
    public Result<Void> saveItinerary(@PathVariable Long id,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        return aiService.saveItinerary(id, resolve(authorization).userId());
    }

    @GetMapping("/suggestions")
    public Result<List<String>> suggestions() {
        return aiService.suggestions();
    }

    /**
     * 问答反馈：C 端点「有用 / 没用 / 转人工」时回写该会话最近一条留痕，
     * 后台 AI 客服与解决率统计据此计算。
     */
    @PostMapping("/conversations/feedback")
    public Result<Void> feedback(@RequestBody Map<String, Object> body,
                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        String sessionId = text(body.get("sessionId"));
        if (!StringUtils.hasText(sessionId)) {
            return Result.fail("缺少会话标识");
        }
        Identity who = resolve(authorization);
        AiConversation conversation = aiConversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getSessionId, sessionId)
                .orderByDesc(AiConversation::getId)
                .last("limit 1"));
        if (conversation == null) {
            return Result.fail("会话不存在");
        }
        if (who.userId() != null && conversation.getUserId() != null
                && !who.userId().equals(conversation.getUserId())) {
            return Result.fail("无权操作该会话");
        }
        AiConversation update = new AiConversation();
        update.setId(conversation.getId());
        if (body.get("resolved") != null) {
            update.setResolved(Boolean.parseBoolean(String.valueOf(body.get("resolved"))) ? 1 : 0);
        }
        if (body.get("transferred") != null) {
            update.setTransferred(Boolean.parseBoolean(String.valueOf(body.get("transferred"))) ? 1 : 0);
        }
        // 两个字段都没传时 updateById 会生成不带 SET 的 UPDATE，直接抛 BadSqlGrammarException 并回 500
        if (update.getResolved() == null && update.getTransferred() == null) {
            return Result.fail("缺少反馈内容");
        }
        aiConversationMapper.updateById(update);
        return Result.ok("感谢反馈", null);
    }

    private record Identity(Long userId, String userName) {
    }

    private Identity resolve(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return new Identity(null, "游客");
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        try {
            Claims claims = jwtUtil.parseToken(token);
            Object id = claims.get("userId");
            if (id == null) {
                return new Identity(null, "管理员");
            }
            Long userId = Long.valueOf(String.valueOf(id));
            User user = userMapper.selectById(userId);
            String name = user != null && StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getMobile();
            return new Identity(userId, name);
        } catch (Exception e) {
            return new Identity(null, "游客");
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 我的问答历史：按会话聚合，进页可续看上一轮对话 */
    @GetMapping("/conversations")
    public Result<java.util.List<com.qingmeili.entity.AiConversation>> conversations(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = resolve(authorization).userId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.qingmeili.entity.AiConversation> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        // 始终按用户过滤：sessionId 只能用来缩小范围，不能跨用户读取
        w.eq(com.qingmeili.entity.AiConversation::getUserId, userId);
        if (sessionId != null && !sessionId.isBlank()) {
            w.eq(com.qingmeili.entity.AiConversation::getSessionId, sessionId);
        }
        w.orderByDesc(com.qingmeili.entity.AiConversation::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        return Result.ok(aiConversationMapper.selectList(w));
    }

    /** 我保存的行程列表 */
    @GetMapping("/itineraries")
    public Result<java.util.List<com.qingmeili.entity.AiItinerary>> itineraries(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = resolve(authorization).userId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(aiItineraryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.qingmeili.entity.AiItinerary>()
                        .eq(com.qingmeili.entity.AiItinerary::getUserId, userId)
                        .orderByDesc(com.qingmeili.entity.AiItinerary::getCreateTime)));
    }
}

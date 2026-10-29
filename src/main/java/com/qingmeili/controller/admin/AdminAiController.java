package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.AiConversation;
import com.qingmeili.entity.AiItinerary;
import com.qingmeili.entity.KbDocument;
import com.qingmeili.mapper.AiConversationMapper;
import com.qingmeili.mapper.AiItineraryMapper;
import com.qingmeili.mapper.KbDocumentMapper;
import com.qingmeili.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台管理 - AI 运营监控
 * 统计、会话明细、行程记录全部来自 C 端真实问答写入的 ai_conversation / ai_itinerary。
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminAiController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final KbDocumentMapper kbDocumentMapper;
    private final AiConversationMapper aiConversationMapper;
    private final AiItineraryMapper aiItineraryMapper;
    private final AiService aiService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<AiConversation> all = aiConversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .orderByAsc(AiConversation::getCreateTime));
        List<KbDocument> docs = kbDocumentMapper.selectList(null);

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        long todayCount = all.stream().filter(c -> !before(c.getCreateTime(), todayStart)).count();
        long monthCount = all.stream().filter(c -> !before(c.getCreateTime(), monthStart)).count();
        long resolved = all.stream().filter(c -> is(c.getResolved())).count();
        long transferred = all.stream().filter(c -> is(c.getTransferred())).count();
        long tokenUsage = all.stream().mapToLong(c -> c.getTokenUsage() == null ? 0 : c.getTokenUsage()).sum();

        List<Map<String, Object>> trend = new ArrayList<>();
        List<Map<String, Object>> hitRateTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<AiConversation> dayRows = all.stream()
                    .filter(c -> c.getCreateTime() != null && c.getCreateTime().toLocalDate().equals(day))
                    .toList();
            trend.add(trendPoint(day.format(DAY), dayRows.size()));
            long hit = dayRows.stream().filter(c -> "KB".equals(c.getSource())).count();
            int rate = dayRows.isEmpty() ? 0 : (int) Math.round(hit * 100.0 / dayRows.size());
            hitRateTrend.add(trendPoint(day.format(DAY), rate));
        }

        List<Map<String, Object>> categoryDist = all.stream()
                .collect(Collectors.groupingBy(c -> StringUtils.hasText(c.getCategory()) ? c.getCategory() : "未分类",
                        LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> rankItem(e.getKey(), e.getValue().intValue()))
                .collect(Collectors.toList());
        if (categoryDist.isEmpty()) {
            categoryDist = docs.stream()
                    .collect(Collectors.groupingBy(d -> StringUtils.hasText(d.getCategory()) ? d.getCategory() : "未分类",
                            LinkedHashMap::new, Collectors.counting()))
                    .entrySet().stream()
                    .map(e -> rankItem(e.getKey(), e.getValue().intValue()))
                    .collect(Collectors.toList());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayConversations", todayCount);
        data.put("monthConversations", monthCount);
        data.put("resolveRate", rate(resolved, all.size()));
        data.put("transferRate", rate(transferred, all.size()));
        data.put("itineraryCount", aiItineraryMapper.selectCount(null));
        data.put("contentCount", docs.size());
        data.put("tokenUsage", tokenUsage);
        data.put("trend", trend);
        data.put("categoryDist", categoryDist);
        data.put("hitRateTrend", hitRateTrend);
        // 单篇知识被命中次数，后台知识库列表据此计算命中率
        Map<String, Long> kbHits = all.stream()
                .filter(c -> c.getKbDocumentId() != null)
                .collect(Collectors.groupingBy(c -> String.valueOf(c.getKbDocumentId()),
                        LinkedHashMap::new, Collectors.counting()));
        data.put("kbHits", kbHits);
        data.put("totalConversations", all.size());
        return Result.ok(data);
    }

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations() {
        List<AiConversation> rows = aiConversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .orderByAsc(AiConversation::getCreateTime));
        Map<String, List<AiConversation>> grouped = rows.stream()
                .collect(Collectors.groupingBy(c -> StringUtils.hasText(c.getSessionId()) ? c.getSessionId() : "s-" + c.getId(),
                        LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> list = new ArrayList<>();
        grouped.forEach((sessionId, items) -> {
            AiConversation first = items.get(0);
            AiConversation last = items.get(items.size() - 1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", last.getId());
            row.put("sessionId", sessionId);
            row.put("user", StringUtils.hasText(last.getUserName()) ? last.getUserName() : "游客");
            row.put("startTime", fmt(first.getCreateTime()));
            row.put("messageCount", items.size() * 2);
            row.put("transferred", items.stream().anyMatch(c -> is(c.getTransferred())));
            row.put("resolved", items.stream().allMatch(c -> is(c.getResolved())));
            row.put("lastMessage", last.getQuestion());
            list.add(row);
        });
        Collections.reverse(list);
        return Result.ok(list.stream().limit(200).collect(Collectors.toList()));
    }

    @GetMapping("/conversations/{sessionId}/messages")
    public Result<List<Map<String, Object>>> messages(@PathVariable String sessionId) {
        List<AiConversation> rows = aiConversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getSessionId, sessionId)
                .orderByAsc(AiConversation::getCreateTime));
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiConversation row : rows) {
            list.add(message("user", row.getQuestion(), fmt(row.getCreateTime())));
            String answer = StringUtils.hasText(row.getAnswer()) ? row.getAnswer() : "（无回答记录）";
            list.add(message("KB".equals(row.getSource()) ? "ai" : (is(row.getTransferred()) ? "human" : "ai"),
                    answer, fmt(row.getCreateTime())));
        }
        return Result.ok(list);
    }

    @GetMapping("/itineraries")
    public Result<List<Map<String, Object>>> itineraries() {
        List<AiItinerary> rows = aiItineraryMapper.selectList(new LambdaQueryWrapper<AiItinerary>()
                .orderByDesc(AiItinerary::getCreateTime)
                .last("limit 200"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiItinerary row : rows) {
            List<String> spots = StringUtils.hasText(row.getSpots())
                    ? Arrays.stream(row.getSpots().split(",")).filter(StringUtils::hasText).toList()
                    : List.of();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("user", StringUtils.hasText(row.getUserName()) ? row.getUserName() : "游客");
            item.put("date", nz(row.getTravelDate()));
            item.put("people", row.getPeople() == null ? 1 : row.getPeople());
            item.put("type", nz(row.getType()));
            item.put("title", nz(row.getTitle()));
            item.put("length", 1);
            item.put("spots", spots);
            item.put("saved", is(row.getSaved()));
            item.put("createdAt", fmt(row.getCreateTime()));
            list.add(item);
        }
        return Result.ok(list);
    }

    /** 后台内容生成：按主题引用库内景点/商品/路线/民宿组织文案 */
    @PostMapping("/content-gen")
    public Result<Map<String, Object>> contentGen(@RequestBody(required = false) Map<String, Object> body) {
        String topic = body == null || body.get("topic") == null ? null : String.valueOf(body.get("topic"));
        return aiService.generateContent(topic);
    }

    private Map<String, Object> message(String role, String text, String time) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("text", nz(text));
        item.put("time", time);
        return item;
    }

    /** 趋势点：{date, value} */
    private Map<String, Object> trendPoint(String date, int value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("date", date);
        item.put("value", value);
        return item;
    }

    /** 分布项：{name, value} */
    private Map<String, Object> rankItem(String name, int value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    private int rate(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    private boolean before(LocalDateTime time, LocalDateTime threshold) {
        return time == null || time.isBefore(threshold);
    }

    private boolean is(Integer flag) {
        return flag != null && flag == 1;
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.format(TIME);
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}

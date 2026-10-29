package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qingmeili.ai.AiCardFactory;
import com.qingmeili.ai.AiDraft;
import com.qingmeili.ai.AiProperties;
import com.qingmeili.ai.MarketingCopy;
import com.qingmeili.ai.embedding.KbVectorIndex;
import com.qingmeili.ai.embedding.VectorHit;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.*;
import com.qingmeili.mapper.AiConversationMapper;
import com.qingmeili.mapper.AiItineraryMapper;
import com.qingmeili.mapper.KbDocumentMapper;
import com.qingmeili.service.AiAgentService;
import com.qingmeili.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.qingmeili.ai.AiCardFactory.nz;
import static com.qingmeili.ai.AiCardFactory.trim;

/**
 * AI 山野向导实现。
 *
 * <p>回答链路（越靠前越优先）：
 * <ol>
 *   <li><b>知识库命中</b>：平台官方说明类问题。标题完整出现时走零成本的 2-gram 字面短路；
 *       否则走向量语义检索（{@code KbVectorIndex}，余弦相似度 + 关键词融合），
 *       能听懂与文档措辞不一致的口语化提问；向量通道不可用时自动退回 2-gram 关键词；</li>
 *   <li><b>大模型 Agent</b>：{@link AiAgentService} 驱动通义千问，带函数调用工具集，
 *       由模型自行查询库内真实数据后组织回答，覆盖开放式的行程、推荐、闲聊类提问；</li>
 *   <li><b>规则引擎</b>：模型不可用（未配 Key、断网、限流）时按意图取真实库数据兜底，
 *       保证任何环境下对话功能都不中断。</li>
 * </ol>
 * 三条链路的产出最终都归一成 {@link AiDraft}，因此前端的出参结构与后台统计口径完全一致。
 */
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    /** 知识库命中阈值，低于该分值视为未命中并转下一级链路 */
    private static final int KB_THRESHOLD = 4;

    /** 入口敏感词：命中直接安全回复，不消耗模型也不留违规生成记录 */
    private static final java.util.List<String> SENSITIVE_WORDS = java.util.List.of(
            "色情", "裸聊", "赌博", "博彩", "毒品", "枪支", "炸药", "暴恐",
            "代开发票", "办证", "刷单", "代刷", "洗钱", "自杀");

    private final KbDocumentMapper kbDocumentMapper;
    private final AiConversationMapper aiConversationMapper;
    private final AiItineraryMapper aiItineraryMapper;
    private final AiCardFactory card;
    private final AiAgentService aiAgentService;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    /** 向量检索器。整块缺失是合法状态（未配置 Key / 关闭向量化），此处按需取用 */
    private final ObjectProvider<KbVectorIndex> vectorIndexProvider;

    // ==================== C端问答 ====================

    @Override
    public Result<JsonNode> chat(String sessionId, String question, Long userId, String userName) {
        String q = question == null ? "" : question.trim();
        if (!StringUtils.hasText(q)) {
            return Result.fail("请输入你的问题");
        }
        String sid = StringUtils.hasText(sessionId)
                ? sessionId
                : "sess-" + UUID.randomUUID().toString().replace("-", "");

        // 入口风控：命中敏感词不走大模型，直接给安全回复，避免生成违规内容
        if (SENSITIVE_WORDS.stream().anyMatch(q::contains)) {
            ObjectNode safe = objectMapper.createObjectNode();
            safe.put("id", "ai-" + System.currentTimeMillis());
            safe.put("role", "assistant");
            safe.put("content", "这个问题我暂时回答不了。我是青梅里的山野向导，可以帮你查路线、采摘、民宿和农产品。");
            safe.put("sessionId", sid);
            safe.put("category", "SAFE");
            safe.put("source", "guard");
            safe.put("resolved", 1);
            safe.putArray("cards");
            return Result.ok(safe);
        }

        AiDraft draft = answer(sid, q, userId, userName);

        ObjectNode reply = objectMapper.createObjectNode();
        reply.put("id", "ai-" + System.currentTimeMillis());
        reply.put("role", "assistant");
        reply.put("content", draft.getContent());
        reply.put("sessionId", sid);
        reply.put("category", draft.getCategory());
        reply.put("source", draft.getSource());
        reply.put("resolved", draft.getResolved());
        ArrayNode cards = reply.putArray("cards");
        draft.getCards().forEach(cards::add);

        record(sid, q, draft, userId, userName);
        return Result.ok(reply);
    }

    /**
     * 回答链路：标题快路径 → 大模型 Agent → 向量语义检索 → 关键词 → 规则引擎。
     *
     * <p><b>为什么把"知识库检索"拆成两条代价不同的路：</b>
     * 每个游客提问都要先判断走哪条链路，如果这里无脑调一次嵌入接口，
     * 每个问题都会平白多出 300-500ms 的网络往返。所以：
     * <ul>
     *   <li>「问题里完整出现了知识库标题」是纯 CPU 的字面判断，放最前面零成本短路；</li>
     *   <li>真正的向量检索只发生在"大模型不可用或调用失败"时——此时它本来就是唯一的兜底手段，
     *       多这一次网络往返是划算的；</li>
     *   <li>大模型可用时，知识库的语义检索由 Agent 自己调工具完成（见 {@code AiAgentTools}），
     *       与在链路前置相比并不会多花一次调用。</li>
     * </ul>
     */
    private AiDraft answer(String sessionId, String q, Long userId, String userName) {
        // 1) 字面确定性命中，直接短路，不做任何网络调用
        KbMatch exact = exactTitleMatch(q);
        if (exact != null) {
            return knowledgeAnswer(exact.doc(), q);
        }
        // 2) 交给 Agent：它自带向量检索工具，官方说明照样查得到
        boolean agentReady = aiAgentService.available();
        AiDraft llm = agentReady ? aiAgentService.ask(sessionId, q, userId, userName) : null;
        if (llm != null) {
            return llm;
        }
        // 3) 大模型不可用（或调用失败）→ 向量语义检索兜底，再退关键词，最后才是规则引擎
        KbMatch semantic = semanticMatch(q);
        if (semantic != null) {
            return knowledgeAnswer(semantic.doc(), q);
        }
        return ruleAnswer(q);
    }

    private AiDraft knowledgeAnswer(KbDocument doc, String q) {
        AiDraft d = new AiDraft();
        d.setContent(doc.getContent() == null ? doc.getTitle() : doc.getContent().trim());
        d.setCategory(StringUtils.hasText(doc.getCategory()) ? doc.getCategory() : "知识库");
        d.setSource("KB");
        d.setKbDocumentId(doc.getId());
        d.setCards(card.relatedCards(d.getCategory(), q));
        return d;
    }

    /** 知识库命中结果：{@code exactTitle} 为 true 表示问题里完整出现了该条目标题，属高置信命中 */
    private record KbMatch(KbDocument doc, boolean exactTitle) {
    }

    /** 标题完整命中：字面证据已经是确定的，置信度最高，且纯 CPU 判断、零网络开销 */
    private KbMatch exactTitleMatch(String q) {
        if (!StringUtils.hasText(q)) {
            return null;
        }
        for (KbDocument doc : card.knowledgeDocuments()) {
            String title = nz(doc.getTitle());
            if (title.length() > 1 && q.contains(title)) {
                return new KbMatch(doc, true);
            }
        }
        return null;
    }

    /**
     * 语义命中：优先走向量检索——它能听懂"临时去不了 ≈ 退改"这类字面上完全不重合的说法；
     * 向量通道不可用、或结果都不达阈值时，再退回 2-gram 关键词检索。
     *
     * <p>阈值用 {@code ai.embedding.rule-match-score}（比 Agent 工具用的 min-score 更保守）：
     * 这里一旦命中就是直接把文档正文当作答案返回给游客，宁可漏召回，也不能答错。
     */
    private KbMatch semanticMatch(String q) {
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        if (index != null && index.available()) {
            double threshold = aiProperties.getEmbedding().getRuleMatchScore();
            for (VectorHit hit : index.search(q, 3, 0)) {
                if (hit.titleHit() || hit.score() >= threshold) {
                    KbDocument doc = kbDocumentMapper.selectById(hit.docId());
                    if (doc != null && doc.getStatus() != null && doc.getStatus() == 1) {
                        return new KbMatch(doc, hit.titleHit());
                    }
                }
            }
        }
        return keywordMatch(q);
    }

    /** 2-gram 重叠打分：标题权重最高，其次分类，正文贡献封顶，避免长文档误命中 */
    private KbMatch keywordMatch(String q) {
        List<KbDocument> docs = card.knowledgeDocuments();
        Set<String> qGrams = AiCardFactory.ngrams(q);
        KbDocument best = null;
        int bestScore = 0;
        int bestTitleOverlap = 0;
        boolean bestTitleHit = false;
        for (KbDocument doc : docs) {
            String title = nz(doc.getTitle());
            boolean titleHit = title.length() > 1 && q.contains(title);
            int titleOverlap = AiCardFactory.overlap(qGrams, AiCardFactory.ngrams(title));
            int categoryOverlap = AiCardFactory.overlap(qGrams, AiCardFactory.ngrams(nz(doc.getCategory())));
            int contentOverlap = Math.min(AiCardFactory.overlap(qGrams, AiCardFactory.ngrams(nz(doc.getContent()))), 3);
            int score = (titleHit ? 8 : 0) + titleOverlap * 3 + categoryOverlap * 2 + contentOverlap;
            if (score > bestScore) {
                bestScore = score;
                bestTitleOverlap = titleOverlap;
                bestTitleHit = titleHit;
                best = doc;
            }
        }
        if (best == null) {
            return null;
        }
        boolean hit = bestTitleHit || (bestScore >= KB_THRESHOLD && bestTitleOverlap >= 2);
        return hit ? new KbMatch(best, bestTitleHit) : null;
    }

    /** 规则引擎：按意图取真实库数据组织回答 */
    private AiDraft ruleAnswer(String q) {
        AiDraft d = new AiDraft();

        if (q.matches(".*(亲子|孩子|带娃|小朋友|研学).*")) {
            d.setCategory("亲子出行");
            TravelRoute route = card.routes(10).stream()
                    .filter(r -> nz(r.getTitle()).contains("亲子") || nz(r.getSubtitle()).contains("研学"))
                    .findFirst().orElse(null);
            if (route == null) {
                route = card.routes(1).stream().findFirst().orElse(null);
            }
            if (route != null) {
                d.getCards().add(card.routeCard(route));
                d.setContent("带娃推荐走「" + route.getTitle() + "」："
                        + nz(route.getSummary())
                        + " 全程" + nz(route.getDifficulty()) + "，途经 " + stopNames(route) + "。");
            } else {
                d.setContent("暂时没有可推荐的亲子路线，可以到「山野旅行 - 路线」查看最新安排。");
                d.setResolved(0);
            }
            card.orchards(2).forEach(o -> d.getCards().add(card.pickingCard(o, null)));
            return d;
        }

        if (q.matches(".*(行程|路线|规划|一天|两天|怎么玩|安排|攻略).*")) {
            d.setCategory("行程规划");
            List<TravelRoute> list = card.routes(2);
            if (list.isEmpty()) {
                d.setContent("路线数据还没有准备好，稍后再问我一次。");
                d.setResolved(0);
                return d;
            }
            list.forEach(r -> d.getCards().add(card.routeCard(r)));
            String detail = list.stream()
                    .map(r -> "「" + r.getTitle() + "」（" + nz(r.getDuration()) + "，" + nz(r.getDifficulty()) + "）")
                    .collect(Collectors.joining("、"));
            d.setContent("为你找到 " + list.size() + " 条可直接照走的路线：" + detail
                    + "。点开卡片能看到逐站时间、停留时长与距离。");
            card.scenics(1).forEach(s -> d.getCards().add(card.scenicCard(s)));
            return d;
        }

        if (q.matches(".*(民宿|住宿|住哪|客栈|房间|入住|睡觉).*")) {
            d.setCategory("住宿指南");
            List<Homestay> list = card.homestays(4);
            if (list.isEmpty()) {
                d.setContent("暂时没有可预订的民宿，可以先看看采摘与景点安排。");
                d.setResolved(0);
                return d;
            }
            list.forEach(h -> d.getCards().add(card.homestayCard(h)));
            String detail = list.stream()
                    .limit(3)
                    .map(h -> h.getName() + (h.getRating() != null ? "（" + card.rating(h.getRating()) + " 分）" : ""))
                    .collect(Collectors.joining("、"));
            d.setContent("村里目前可住 " + list.size() + " 家，推荐 " + detail + "。点开卡片可看房型、设施与可订日期。");
            return d;
        }

        if (q.matches(".*(采摘|摘果|青梅季|什么时候|几月|预约|场次|果园).*")) {
            d.setCategory("游玩指南");
            List<Orchard> list = card.orchards(3);
            Map<Long, PickingSession> earliest = card.earliestSessions();
            if (list.isEmpty()) {
                d.setContent("当前没有开放预约的果园，青梅季一般在 5 月前后，可以先逛逛乡村好物。");
                d.setResolved(0);
                return d;
            }
            list.forEach(o -> d.getCards().add(card.pickingCard(o, earliest.get(o.getId()))));
            Orchard first = list.get(0);
            PickingSession s = earliest.get(first.getId());
            d.setContent(s == null
                    ? "「" + first.getName() + "」等 " + list.size() + " 个果园支持采摘体验，具体场次请在卡片里选日期预约，到场记得穿防滑鞋。"
                    : "最近可约的是「" + first.getName() + "」" + s.getSlotDate().format(AiCardFactory.MMDD) + " " + nz(s.getTimeRange())
                        + "，¥" + card.price(s.getPrice()) + "/人，余 " + card.remain(s) + " 位；建议提前半天锁定场次。");
            return d;
        }

        if (q.matches(".*(农产品|特产|好物|买|多少钱|价格|伴手礼|梅酒|果干|蜂蜜|下单).*")) {
            d.setCategory("乡村好物");
            List<Product> list = card.products(4, card.keywordOf(q));
            if (list.isEmpty()) {
                d.setContent("商城暂时没有匹配的商品，换个关键词或直接到「青梅好物」逛逛。");
                d.setResolved(0);
                return d;
            }
            list.forEach(p -> d.getCards().add(card.productCard(p)));
            String detail = list.stream()
                    .limit(3)
                    .map(p -> p.getName() + " ¥" + card.price(p.getPrice()))
                    .collect(Collectors.joining("、"));
            d.setContent("这几款是村里卖得最好的：" + detail + "。都支持在线下单与配送，点开卡片可看规格与详情。");
            return d;
        }

        if (q.matches(".*(景点|打卡|拍照|哪里好玩|看什么|风景|日落|观景).*")) {
            d.setCategory("游玩指南");
            List<Scenic> list = card.scenics(4);
            if (list.isEmpty()) {
                d.setContent("景点数据还没有准备好，稍后再问我一次。");
                d.setResolved(0);
                return d;
            }
            list.forEach(s -> d.getCards().add(card.scenicCard(s)));
            String detail = list.stream().limit(3).map(Scenic::getName).collect(Collectors.joining("、"));
            d.setContent("值得走一趟的有 " + detail + " 等 " + list.size() + " 处，卡片里能看到开放时间、评分与讲解音频。");
            return d;
        }

        if (q.matches(".*(怎么去|交通|停车|地址|导航|在哪|多远|开车).*")) {
            d.setCategory("出行交通");
            List<Scenic> list = card.scenics(2);
            list.forEach(s -> d.getCards().add(card.scenicCard(s)));
            String where = list.isEmpty() ? "青梅乡村游客中心" : list.get(0).getName();
            d.setContent("导航直接搜索「" + where + "」即可，村口游客中心有免费停车位；"
                    + "景点之间距离 1-4km，步行或骑行都能到，具体位置见卡片。");
            return d;
        }

        d.setCategory("其他");
        d.setResolved(0);
        d.setContent("这个问题我还答不上来，已记录给运营人工跟进。你可以问我：怎么安排一日行程、最近能采摘吗、"
                + "有哪些民宿、青梅好物推荐、景点怎么走过去。");
        return d;
    }

    private void record(String sessionId, String question, AiDraft d, Long userId, String userName) {
        AiConversation row = new AiConversation();
        row.setSessionId(sessionId);
        row.setUserId(userId);
        row.setUserName(StringUtils.hasText(userName) ? userName : "游客");
        row.setQuestion(question);
        row.setAnswer(d.getContent());
        row.setCategory(d.getCategory());
        row.setKbDocumentId(d.getKbDocumentId());
        row.setSource(d.getSource());
        row.setResolved(d.getResolved());
        row.setTransferred(d.getResolved() == 0 ? 1 : 0);
        // 大模型链路已按真实调用量估算，KB / 规则链路在此按文本长度折算
        row.setTokenUsage(d.getTokenUsage() > 0
                ? d.getTokenUsage()
                : AiDraft.estimateTokens(question, d.getContent(), d.getCards().size()));
        aiConversationMapper.insert(row);
    }

    // ==================== C端行程生成 ====================

    @Override
    public Result<JsonNode> itinerary(String date, Integer people, String type, Long userId, String userName) {
        String travelDate = StringUtils.hasText(date) ? date : LocalDate.now().toString();
        int headcount = people == null || people <= 0 ? 2 : people;
        String theme = StringUtils.hasText(type) ? type : "休闲";

        TravelRoute route = card.routes(10).stream()
                .filter(r -> matchTheme(r, theme))
                .findFirst()
                .orElseGet(() -> card.routes(1).stream().findFirst().orElse(null));

        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("id", "plan-" + System.currentTimeMillis());
        plan.put("date", travelDate);
        plan.put("type", theme);
        plan.put("people", headcount);

        ArrayNode stops = plan.putArray("stops");
        String title;
        if (route != null && routeItinerary(route, stops)) {
            title = theme + " · " + route.getTitle();
            plan.put("routeId", card.routePublicId(route));
            plan.put("routePath", "pages/travel/route-detail?id=" + card.routePublicId(route));
            plan.put("summary", nz(route.getSummary()));
            plan.put("difficulty", nz(route.getDifficulty()));
            plan.put("bestTime", nz(route.getBestTime()));
        } else {
            title = theme + " · 一日青梅行程";
            composeFromLibrary(stops);
        }
        plan.put("title", title);

        ArrayNode tips = plan.putArray("tips");
        tips.add("行程共 " + stops.size() + " 站，按 " + headcount + " 人出行安排，采摘与民宿建议提前预约。");
        if (route != null && StringUtils.hasText(route.getDetail())) {
            readArray(route.getDetail(), "tips").forEach(t -> tips.add(t.asText()));
        }

        persistItinerary(plan, stops, travelDate, theme, headcount, userId, userName);
        return Result.ok(plan);
    }

    @Override
    public Result<Void> saveItinerary(Long id, Long userId) {
        if (id == null) {
            return Result.fail("缺少行程 ID");
        }
        AiItinerary row = aiItineraryMapper.selectById(id);
        if (row == null) {
            return Result.fail("行程不存在或已删除");
        }
        if (userId != null && row.getUserId() != null && !userId.equals(row.getUserId())) {
            return Result.fail("不能操作他人的行程");
        }
        AiItinerary update = new AiItinerary();
        update.setId(id);
        update.setSaved(1);
        aiItineraryMapper.updateById(update);
        return Result.ok();
    }

    private boolean matchTheme(TravelRoute route, String theme) {
        String text = nz(route.getTitle()) + nz(route.getSubtitle()) + nz(route.getSummary()) + nz(route.getDetail());
        if ("亲子".equals(theme)) {
            return text.contains("亲子") || text.contains("研学");
        }
        if ("休闲".equals(theme)) {
            return text.contains("轻氧") || text.contains("缓坡") || text.contains("休闲");
        }
        return text.contains(theme);
    }

    /** 用路线富明细里的逐站安排填充 stops */
    private boolean routeItinerary(TravelRoute route, ArrayNode stops) {
        JsonNode itinerary = route.getDetail() == null ? null : readNode(route.getDetail(), "itinerary");
        if (itinerary == null || !itinerary.isArray() || itinerary.isEmpty()) {
            return false;
        }
        for (JsonNode item : itinerary) {
            ObjectNode stop = stops.addObject();
            stop.put("id", item.path("id").asText("it-" + (stops.size() + 1)));
            stop.put("time", item.path("time").asText(""));
            stop.put("title", item.path("title").asText(""));
            stop.put("image", item.path("image").asText(""));
            stop.put("description", item.path("desc").asText(item.path("description").asText("")));
            stop.put("distance", item.path("distance").asText(""));
            stop.put("stay", item.path("stay").asText(""));
            stop.put("tag", item.path("tag").asText(""));
        }
        return true;
    }

    /** 无路线明细时，用果园 / 景点 / 民宿真实数据现编一日行程 */
    private void composeFromLibrary(ArrayNode stops) {
        Orchard orchard = card.orchards(1).stream().findFirst().orElse(null);
        if (orchard != null) {
            addStop(stops, "09:00", orchard.getName(), nz(orchard.getCoverImage()),
                    nz(orchard.getIntro()), "起点");
        }
        List<Scenic> list = card.scenics(2);
        String[] times = {"11:30", "14:00"};
        for (int i = 0; i < list.size(); i++) {
            Scenic s = list.get(i);
            addStop(stops, times[i], s.getName(), nz(s.getCoverImage()), nz(s.getSummary()),
                    StringUtils.hasText(s.getDistance()) ? "距上站 " + s.getDistance() : "");
        }
        Homestay homestay = card.homestays(1).stream().findFirst().orElse(null);
        if (homestay != null) {
            addStop(stops, "16:00", homestay.getName() + " 入住", nz(homestay.getCoverImage()),
                    nz(homestay.getIntro()), homestay.getAddress() == null ? "" : homestay.getAddress());
        }
        Product product = card.products(1, null).stream().findFirst().orElse(null);
        if (product != null) {
            addStop(stops, "18:00", "带一份 " + product.getName() + " 回家", nz(product.getCoverImage()),
                    nz(product.getSummary()), "民宿内");
        }
    }

    private void addStop(ArrayNode stops, String time, String title, String image, String desc, String distance) {
        ObjectNode stop = stops.addObject();
        stop.put("id", "it-" + (stops.size() + 1));
        stop.put("time", time);
        stop.put("title", title);
        stop.put("image", image);
        stop.put("description", desc);
        stop.put("distance", distance);
    }

    private void persistItinerary(ObjectNode plan, ArrayNode stops, String travelDate, String theme,
                                  int headcount, Long userId, String userName) {
        List<String> names = new ArrayList<>();
        stops.forEach(s -> names.add(s.path("title").asText()));
        AiItinerary row = new AiItinerary();
        row.setUserId(userId);
        row.setUserName(StringUtils.hasText(userName) ? userName : "游客");
        row.setTitle(plan.path("title").asText(theme + " · 一日青梅行程"));
        row.setTravelDate(travelDate);
        row.setType(theme);
        row.setPeople(headcount);
        row.setSpots(String.join(",", names));
        row.setSaved(0);
        try {
            row.setContent(objectMapper.writeValueAsString(plan));
        } catch (Exception ignored) {
            row.setContent(null);
        }
        aiItineraryMapper.insert(row);
        plan.put("recordId", row.getId() == null ? 0 : row.getId());
    }

    // ==================== C端推荐问题 ====================

    @Override
    public Result<List<String>> suggestions() {
        List<KbDocument> docs = kbDocumentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, 1)
                .eq(KbDocument::getAuditStatus, 1)
                .orderByAsc(KbDocument::getId)
                .last("limit 50"));
        List<String> titles = docs.stream().map(KbDocument::getTitle).filter(StringUtils::hasText).toList();
        // 疑问句式标题更适合直接当作推荐问题展示
        List<String> list = titles.stream().filter(AiServiceImpl::isQuestion).collect(Collectors.toList());
        titles.stream().filter(t -> !isQuestion(t)).forEach(list::add);
        list = list.stream().limit(8).collect(Collectors.toList());
        for (String fallback : List.of("怎么安排一日行程？", "最近能采摘青梅吗？", "有哪些民宿推荐？", "青梅好物买什么？")) {
            if (list.size() >= 6) {
                break;
            }
            if (!list.contains(fallback)) {
                list.add(fallback);
            }
        }
        return Result.ok(list);
    }

    private static boolean isQuestion(String title) {
        return title.contains("？") || title.contains("?");
    }

    // ==================== 后台内容生成 ====================

    @Override
    public Result<Map<String, Object>> generateContent(String topic) {
        String t = StringUtils.hasText(topic) ? topic.trim() : "青梅采摘活动";
        List<Scenic> scenics = card.scenics(3);
        List<Product> products = card.products(3, card.keywordOf(t));
        List<TravelRoute> routes = card.routes(2);
        List<Homestay> homestays = card.homestays(2);
        Map<Long, PickingSession> earliest = card.earliestSessions();
        Orchard orchard = card.orchards(1).stream().findFirst().orElse(null);

        String spotText = scenics.stream().map(Scenic::getName).collect(Collectors.joining("、"));
        String goodsText = products.stream()
                .map(p -> p.getName() + "（¥" + card.price(p.getPrice()) + "）")
                .collect(Collectors.joining("、"));
        String stayText = homestays.stream().map(Homestay::getName).collect(Collectors.joining("、"));
        String routeText = routes.stream().map(TravelRoute::getTitle).collect(Collectors.joining("、"));
        String pickText = orchard == null ? "青梅半山果园" : orchard.getName();
        PickingSession session = orchard == null ? null : earliest.get(orchard.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", t + "·青梅里乡村体验季");

        // 素材简报：无论交给大模型润色还是走模板，都只允许引用这些库内真实素材
        String brief = "活动主题：" + t
                + "\n采摘园：" + pickText
                + (session == null ? "" : "（最近场次 " + session.getSlotDate().format(AiCardFactory.MMDD)
                    + " " + nz(session.getTimeRange()) + "，¥" + card.price(session.getPrice()) + "/人）")
                + "\n可引用景点：" + orElse(spotText, "无")
                + "\n可引用商品：" + orElse(goodsText, "无")
                + "\n可引用路线：" + orElse(routeText, "无")
                + "\n可引用民宿：" + orElse(stayText, "无")
                + "\n请围绕该主题写一份青梅里乡村文旅活动文案。";

        MarketingCopy copy = aiAgentService.writeCopy(brief);
        if (copy != null) {
            data.put("intro", nz(copy.getIntro()));
            data.put("copy", nz(copy.getCopy()));
            data.put("tags", copy.getTags() == null || copy.getTags().isEmpty()
                    ? templateTags(t, routeText)
                    : String.join(",", copy.getTags().stream().limit(6).toList()));
            data.put("generatedBy", "LLM");
        } else {
            data.put("intro", templateIntro(t, pickText, session, spotText));
            data.put("copy", templateCopy(t, orchard, scenics, products, goodsText, homestays, stayText));
            data.put("tags", templateTags(t, routeText));
            data.put("generatedBy", "RULE");
        }

        String seo = t + ",青梅里乡村文旅," + pickText + "采摘预约,"
                + (scenics.isEmpty() ? "乡村景点" : scenics.get(0).getName()) + ",亲子旅游攻略";
        data.put("seo", seo);
        // 封面与档期同样取自库内数据，后台编辑后可直接发布为活动
        data.put("cover", !scenics.isEmpty() ? nz(scenics.get(0).getCoverImage())
                : (!products.isEmpty() ? nz(products.get(0).getCoverImage()) : ""));
        LocalDate today = LocalDate.now();
        data.put("startTime", today.toString());
        data.put("endTime", today.plusDays(14).toString());
        data.put("materials", List.of(
                material("scenic", scenics.stream().map(Scenic::getName).toList()),
                material("product", products.stream().map(Product::getName).toList()),
                material("route", routes.stream().map(TravelRoute::getTitle).toList()),
                material("homestay", homestays.stream().map(Homestay::getName).toList())));
        return Result.ok(data);
    }

    /** 模板导语：大模型不可用时的兜底 */
    private String templateIntro(String t, String pickText, PickingSession session, String spotText) {
        return "「" + t + "」是青梅里推出的沉浸式乡村文旅活动，主场设在 " + pickText
                + (session == null ? "" : "（" + session.getSlotDate().format(AiCardFactory.MMDD) + " " + nz(session.getTimeRange())
                        + "，¥" + card.price(session.getPrice()) + "/人）")
                + "，串联 " + spotText + " 等点位，适合亲子家庭与周末短途出游。";
    }

    /** 模板正文：大模型不可用时的兜底 */
    private String templateCopy(String t, Orchard orchard, List<Scenic> scenics, List<Product> products,
                                String goodsText, List<Homestay> homestays, String stayText) {
        StringBuilder copy = new StringBuilder();
        copy.append("这个季节，来青梅里赴一场「").append(t).append("」之约。");
        if (orchard != null) {
            copy.append("在 ").append(orchard.getName()).append(" 亲手摘下枝头青梅");
            if (StringUtils.hasText(orchard.getIntro())) {
                copy.append("——").append(trim(orchard.getIntro(), 40));
            }
            copy.append("。");
        }
        if (!scenics.isEmpty()) {
            copy.append("随后沿 ").append(scenics.get(0).getName()).append(" 慢行，");
            if (scenics.size() > 1) {
                copy.append("再到 ").append(scenics.get(1).getName()).append(" 看一场日落。");
            } else {
                copy.append("把节奏彻底放慢。");
            }
        }
        if (!products.isEmpty()) {
            copy.append("离场前别忘了带上 ").append(goodsText).append(" 等乡村好物。");
        }
        if (!homestays.isEmpty()) {
            copy.append("想住一晚，").append(stayText).append(" 已备好山景房与晚茶。");
        }
        return copy.toString();
    }

    private String templateTags(String t, String routeText) {
        return Stream.of(t, "乡村游", "采摘体验", "亲子", "周末好去处", routeText)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(6)
                .collect(Collectors.joining(","));
    }

    /** 生成文案引用的库内素材，后台可核对来源 */
    private Map<String, Object> material(String type, List<String> names) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("names", names);
        return item;
    }

    private String orElse(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    // ==================== 工具 ====================

    private String stopNames(TravelRoute route) {
        List<String> names = new ArrayList<>();
        JsonNode stops = route.getStops() == null ? null : readNode(route.getStops(), null);
        if (stops != null && stops.isArray()) {
            stops.forEach(s -> names.add(s.asText()));
        }
        if (names.isEmpty() && route.getDetail() != null) {
            readArray(route.getDetail(), "itinerary").forEach(s -> names.add(s.path("title").asText()));
        }
        return names.isEmpty() ? "村内主要点位" : names.stream().limit(4).collect(Collectors.joining(" → "));
    }

    private JsonNode readNode(String json, String field) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return field == null ? root : root.get(field);
        } catch (Exception e) {
            return null;
        }
    }

    private List<JsonNode> readArray(String json, String field) {
        JsonNode node = readNode(json, field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> list = new ArrayList<>();
        node.forEach(list::add);
        return list;
    }
}

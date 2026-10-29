package com.qingmeili.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qingmeili.ai.embedding.KbVectorIndex;
import com.qingmeili.ai.embedding.VectorHit;
import com.qingmeili.ai.weather.WeatherClient;
import com.qingmeili.ai.weather.WeatherSnapshot;
import com.qingmeili.entity.*;
import com.qingmeili.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 模块共用的「真实数据 + 卡片」工厂。
 *
 * <p>山野向导的每一条回答、每一张推荐卡片都必须来自库内真实数据，因此把
 * <ul>
 *   <li>带状态过滤的查询（只取上架/审核通过/启用的记录）</li>
 *   <li>卡片 JSON 的拼装（含 C 端落地页路径）</li>
 * </ul>
 * 集中在这里，由规则引擎（{@code AiServiceImpl}）与 LLM Agent 的工具集（{@link AiAgentTools}）
 * 共同复用，避免两套实现口径不一致导致「AI 说有的商品，点进去却不存在」。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiCardFactory {

    /** C 端展示用的短日期格式，如 05-18 */
    public static final DateTimeFormatter MMDD = DateTimeFormatter.ofPattern("MM-dd");

    private final KbDocumentMapper kbDocumentMapper;
    private final ProductMapper productMapper;
    private final HomestayMapper homestayMapper;
    private final ScenicMapper scenicMapper;
    private final TravelRouteMapper travelRouteMapper;
    private final OrchardMapper orchardMapper;
    private final PickingSessionMapper pickingSessionMapper;
    private final ObjectMapper objectMapper;
    /**
     * 向量检索器。整块缺失是合法状态（未配置 Key / 关闭向量化），因此用 ObjectProvider 惰性取用，
     * 取不到就退回"只注入标题"的旧行为，绝不因为向量库不可用而启动失败。
     */
    private final ObjectProvider<KbVectorIndex> vectorIndexProvider;
    /** 读取接地开关与注入预算 */
    private final AiProperties aiProperties;

    /**
     * 天气能力探测。只用来在「平台资料」里如实声明"本平台有没有实时天气"。
     *
     * <p>不声明不行：实测未配置天气服务时问「今天天气怎么样」，模型会编出
     * 「多云 22℃ 湿度 65%，未来三天…」这样带精确数字的假预报。
     */
    private final ObjectProvider<WeatherClient> weatherProvider;

    // ==================== 真实数据查询 ====================

    /** 上架中的推荐路线 */
    public List<TravelRoute> routes(int limit) {
        return travelRouteMapper.selectList(new LambdaQueryWrapper<TravelRoute>()
                .eq(TravelRoute::getStatus, 1)
                .orderByAsc(TravelRoute::getSort)
                .orderByAsc(TravelRoute::getId)
                .last("limit " + limit));
    }

    /** 启用中的景点 */
    public List<Scenic> scenics(int limit) {
        return scenicMapper.selectList(new LambdaQueryWrapper<Scenic>()
                .eq(Scenic::getStatus, 1)
                .orderByAsc(Scenic::getSort)
                .orderByAsc(Scenic::getId)
                .last("limit " + limit));
    }

    /** 可预订民宿（上架且审核通过），按评分倒序 */
    public List<Homestay> homestays(int limit) {
        return homestayMapper.selectList(new LambdaQueryWrapper<Homestay>()
                .eq(Homestay::getStatus, 1)
                .eq(Homestay::getAuditStatus, 1)
                .orderByDesc(Homestay::getRating)
                .orderByAsc(Homestay::getId)
                .last("limit " + limit));
    }

    /** 开放中的采摘园，按评分倒序 */
    public List<Orchard> orchards(int limit) {
        return orchardMapper.selectList(new LambdaQueryWrapper<Orchard>()
                .eq(Orchard::getStatus, 1)
                .orderByDesc(Orchard::getRating)
                .orderByAsc(Orchard::getId)
                .last("limit " + limit));
    }

    /** 在售商品，可按关键词模糊匹配名称/简介 */
    public List<Product> products(int limit, String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getOnShelf, 1)
                .eq(Product::getAuditStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Product::getName, keyword).or().like(Product::getSummary, keyword));
        }
        return productMapper.selectList(wrapper.orderByDesc(Product::getSales).orderByAsc(Product::getId)
                .last("limit " + limit));
    }

    /** 每个采摘园最近一个可约场次（key = orchardId） */
    public Map<Long, PickingSession> earliestSessions() {
        List<PickingSession> sessions = pickingSessionMapper.selectList(new LambdaQueryWrapper<PickingSession>()
                .eq(PickingSession::getStatus, 1)
                .ge(PickingSession::getSlotDate, LocalDate.now())
                .orderByAsc(PickingSession::getSlotDate));
        Map<Long, PickingSession> map = new LinkedHashMap<>();
        for (PickingSession s : sessions) {
            if (s.getOrchardId() != null && !map.containsKey(s.getOrchardId())) {
                map.put(s.getOrchardId(), s);
            }
        }
        return map;
    }

    /** 全部生效的知识库条目（启用 + 审核通过），供关键词检索与 Agent 工具复用 */
    public List<KbDocument> knowledgeDocuments() {
        return kbDocumentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, 1)
                .eq(KbDocument::getAuditStatus, 1));
    }

    // ==================== 卡片构建 ====================

    public ObjectNode card(String id, String type, String title, String description, String image, String path) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.put("title", nz(title));
        node.put("description", trim(nz(description), 60));
        node.put("image", nz(image));
        node.put("path", nz(path));
        return node;
    }

    public ObjectNode productCard(Product p) {
        return card("product-" + p.getId(), "product", p.getName(),
                StringUtils.hasText(p.getSummary()) ? p.getSummary()
                        : "¥" + price(p.getPrice()) + " · 已售 " + sales(p.getSales()),
                p.getCoverImage(), "pages/mall/product-detail?id=" + p.getId());
    }

    public ObjectNode homestayCard(Homestay h) {
        String desc = (h.getRating() != null ? rating(h.getRating()) + " 分 · " : "")
                + (StringUtils.hasText(h.getAddress()) ? h.getAddress() : nz(h.getIntro()));
        return card("homestay-" + h.getId(), "homestay", h.getName(), desc, h.getCoverImage(),
                "pages/travel/homestay-detail?id=" + h.getId());
    }

    public ObjectNode scenicCard(Scenic s) {
        String desc = (s.getRating() != null ? rating(s.getRating()) + " 分 · " : "")
                + (StringUtils.hasText(s.getDistance()) ? s.getDistance() + " · " : "")
                + (StringUtils.hasText(s.getSummary()) ? s.getSummary() : nz(s.getLocation()));
        return card("scenic-" + s.getId(), "scenic", s.getName(), desc, s.getCoverImage(),
                "pages/travel/scenic-detail?id=" + publicId(s.getCode(), s.getId()));
    }

    public ObjectNode routeCard(TravelRoute r) {
        String desc = (StringUtils.hasText(r.getDuration()) ? r.getDuration() + " · " : "")
                + (StringUtils.hasText(r.getSummary()) ? r.getSummary() : nz(r.getSubtitle()));
        return card("route-" + r.getId(), "route", r.getTitle(), desc, r.getCoverImage(),
                "pages/travel/route-detail?id=" + routePublicId(r));
    }

    public ObjectNode pickingCard(Orchard o, PickingSession session) {
        String desc = session == null
                ? (StringUtils.hasText(o.getAddress()) ? o.getAddress() : nz(o.getIntro()))
                : session.getSlotDate().format(MMDD) + " " + nz(session.getTimeRange())
                    + " · ¥" + price(session.getPrice()) + "/人 · 余 " + remain(session) + " 位";
        return card("orchard-" + o.getId(), "picking", o.getName(), desc, o.getCoverImage(),
                "pages/travel/picking-detail?id=" + o.getId());
    }

    /**
     * 把 Agent 给出的「类型:id」引用解析为真实卡片。
     *
     * <p>这是防止大模型幻觉的关键闸门：引用必须能在库中查到、且处于可见状态，
     * 否则直接丢弃——宁可少一张卡片，也不让 C 端用户点进一个不存在的详情页。
     *
     * <p>类型名同时兼容英文（prompt 里要求的 product/homestay/...）与中文（模型偶尔会写「民宿:7」）。
     */
    public List<ObjectNode> resolveRefs(List<String> refs) {
        List<ObjectNode> cards = new ArrayList<>();
        if (refs == null || refs.isEmpty()) {
            return cards;
        }
        Map<Long, PickingSession> sessions = null;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String ref : refs) {
            if (!StringUtils.hasText(ref)) {
                continue;
            }
            String[] parts = ref.trim().split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String type = normalizeType(parts[0]);
            Long id;
            try {
                id = Long.valueOf(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (!seen.add(type + ":" + id)) {
                continue;
            }
            switch (type) {
                case "product" -> {
                    Product p = productMapper.selectById(id);
                    if (p != null && is(p.getOnShelf()) && is(p.getAuditStatus())) {
                        cards.add(productCard(p));
                    }
                }
                case "homestay" -> {
                    Homestay h = homestayMapper.selectById(id);
                    if (h != null && is(h.getStatus()) && is(h.getAuditStatus())) {
                        cards.add(homestayCard(h));
                    }
                }
                case "scenic" -> {
                    Scenic s = scenicMapper.selectById(id);
                    if (s != null && is(s.getStatus())) {
                        cards.add(scenicCard(s));
                    }
                }
                case "route" -> {
                    TravelRoute r = travelRouteMapper.selectById(id);
                    if (r != null && is(r.getStatus())) {
                        cards.add(routeCard(r));
                    }
                }
                case "picking" -> {
                    Orchard o = orchardMapper.selectById(id);
                    if (o != null && is(o.getStatus())) {
                        if (sessions == null) {
                            sessions = earliestSessions();
                        }
                        cards.add(pickingCard(o, sessions.get(o.getId())));
                    }
                }
                default -> {
                    // 未知类型忽略
                }
            }
            if (cards.size() >= 4) {
                break;
            }
        }
        return cards;
    }

    /** 卡片类型归一化：统一成内部使用的英文标识 */
    private static String normalizeType(String raw) {
        String t = nz(raw).toLowerCase(Locale.ROOT);
        return switch (t) {
            case "product", "商品", "好物", "农产品", "特产", "乡村好物" -> "product";
            case "homestay", "民宿", "住宿" -> "homestay";
            case "scenic", "景点" -> "scenic";
            case "route", "路线", "行程" -> "route";
            case "picking", "采摘", "果园", "采摘园" -> "picking";
            default -> t;
        };
    }

    /** 知识库命中后附带的关联卡片：先按分类选池子，再按问题相关度排序 */
    public List<ObjectNode> relatedCards(String category, String q) {
        List<ObjectNode> pool = new ArrayList<>();
        String c = nz(category);
        if (c.contains("住宿")) {
            homestays(6).forEach(h -> pool.add(homestayCard(h)));
        } else if (c.contains("好物") || c.contains("商品") || c.contains("商城")) {
            products(6, keywordOf(q)).forEach(p -> pool.add(productCard(p)));
        } else if (c.contains("行程") || c.contains("路线")) {
            routes(3).forEach(r -> pool.add(routeCard(r)));
        } else if (c.contains("游玩") || c.contains("采摘")) {
            Map<Long, PickingSession> earliest = earliestSessions();
            orchards(4).forEach(o -> pool.add(pickingCard(o, earliest.get(o.getId()))));
            scenics(3).forEach(s -> pool.add(scenicCard(s)));
        } else {
            scenics(3).forEach(s -> pool.add(scenicCard(s)));
            products(3, keywordOf(q)).forEach(p -> pool.add(productCard(p)));
            homestays(2).forEach(h -> pool.add(homestayCard(h)));
        }
        Set<String> qGrams = ngrams(q);
        return pool.stream()
                .sorted(Comparator.comparingInt((ObjectNode card) ->
                        overlap(qGrams, ngrams(card.path("title").asText())) * 3
                                + overlap(qGrams, ngrams(card.path("description").asText()))).reversed())
                .limit(4)
                .collect(Collectors.toList());
    }

    /** 从问题里提取可用于商品模糊搜索的关键词 */
    public String keywordOf(String q) {
        String[] known = {"青梅酒", "气泡酒", "果干", "蜂蜜", "梅子", "青梅", "礼盒", "茶叶", "笋", "腊肉"};
        String text = nz(q);
        for (String k : known) {
            if (text.contains(k)) {
                return k;
            }
        }
        return null;
    }

    // ==================== 平台资料摘要（供 Agent 接地） ====================

    /**
     * 把平台当前可见的真实数据压成一段紧凑文本，随每次提问一起送给大模型。
     *
     * <p>为什么不完全依赖模型的函数调用：实测百炼的 qwen 系列在系统提示较长、
     * 或问题较开放（如「怎么安排一日行程」）时，会跳过工具直接凭记忆回答并编造民宿名与价格。
     * 把真实数据确定性地放进上下文，接地就不再取决于模型的"心情"——
     * 工具仍然保留，用于按需钻取摘要里放不下的细节（地址、简介、规则原文）。
     *
     * <p>村域平台数据量很小（十几条），一次全量注入也只有几百字，成本可忽略。
     */
    public String platformDigest() {
        StringBuilder sb = new StringBuilder();

        List<Homestay> homestays = homestays(50);
        if (!homestays.isEmpty()) {
            sb.append("民宿(id|名称|评分|地址)：");
            sb.append(homestays.stream()
                    .map(h -> h.getId() + "|" + nz(h.getName()) + "|" + rating(h.getRating()) + "|" + nz(h.getAddress()))
                    .collect(Collectors.joining("；"))).append('\n');
        }

        List<Product> products = products(50, null);
        if (!products.isEmpty()) {
            sb.append("好物(id|名称|价格|销量)：");
            sb.append(products.stream()
                    .map(p -> p.getId() + "|" + nz(p.getName()) + "|¥" + price(p.getPrice()) + "|" + sales(p.getSales()))
                    .collect(Collectors.joining("；"))).append('\n');
        }

        List<Scenic> scenics = scenics(50);
        if (!scenics.isEmpty()) {
            sb.append("景点(id|名称|评分|距离)：");
            sb.append(scenics.stream()
                    .map(s -> s.getId() + "|" + nz(s.getName()) + "|" + rating(s.getRating()) + "|" + nz(s.getDistance()))
                    .collect(Collectors.joining("；"))).append('\n');
        }

        List<TravelRoute> routes = routes(50);
        if (!routes.isEmpty()) {
            sb.append("路线(id|标题|时长|难度)：");
            sb.append(routes.stream()
                    .map(r -> r.getId() + "|" + nz(r.getTitle()) + "|" + nz(r.getDuration()) + "|" + nz(r.getDifficulty()))
                    .collect(Collectors.joining("；"))).append('\n');
        }

        List<Orchard> orchards = orchards(50);
        if (!orchards.isEmpty()) {
            Map<Long, PickingSession> earliest = earliestSessions();
            sb.append("采摘园(id|名称|最近场次|价格|余位)：");
            sb.append(orchards.stream().map(o -> {
                PickingSession s = earliest.get(o.getId());
                String slot = s == null ? "暂无可约场次"
                        : s.getSlotDate().format(MMDD) + " " + nz(s.getTimeRange());
                String priceText = s == null ? "-" : "¥" + price(s.getPrice());
                String remainText = s == null ? "-" : remain(s) + "位";
                return o.getId() + "|" + nz(o.getName()) + "|" + slot + "|" + priceText + "|" + remainText;
            }).collect(Collectors.joining("；"))).append('\n');
        }

        List<KbDocument> docs = knowledgeDocuments();
        if (!docs.isEmpty()) {
            sb.append("官方说明条目(标题)：");
            sb.append(docs.stream().map(KbDocument::getTitle).filter(StringUtils::hasText)
                    .collect(Collectors.joining("、")));
            sb.append('\n');
        }

        sb.append(weatherCapabilityLine());
        return sb.toString().trim();
    }

    /**
     * 天气能力声明 —— 把「本平台有没有实时天气」写进「平台资料」。
     *
     * <p>为什么要写：系统提示约定"回答只能依据平台资料、资料里没有的就说查不到"，
     * 但天气恰好落在模型的常识舒适区。实测未配置天气服务时问「今天天气怎么样」，
     * 模型答出「多云 22℃、湿度 65%、未来三天多云转晴」—— 数字精确得像真的，
     * 而库里一个天气字段都没有。单靠系统提示里那句"绝不编造"拦不住。
     *
     * <p>所以把事实显式写进资料：有能力就引导它调工具，没有就明说"查不到、别估计"。
     * 与知识库正文接地同一套思路 —— 用确定性注入补上模型的自觉。
     *
     * <p><b>为什么接入后还要再进一步：把快照数据直接写进资料，而不是只说"请调用工具"。</b>
     * 实测接入天气服务后问「今天天气怎么样？」（一个很短的问句），模型<b>没有</b>去调工具，
     * 直接凭常识答了「多云 22℃、湿度 65%」——而真实实况是 19℃。同一轮里问
     * 「明天适合来采摘吗，要带伞吗？」它却乖乖调了工具、答案完全正确。
     * 也就是说：<b>是否触发工具调用取决于模型当天的心情，不可依赖</b>。
     * 而天气快照自带 30 分钟缓存、文本不过百字，确定性地放进资料的成本几乎为零，
     * 收益是"任何问法都不会编"。这与知识库正文接地的取舍完全一致。
     */
    private String weatherCapabilityLine() {
        WeatherClient weather = weatherProvider.getIfAvailable();
        if (weather == null || !weather.available()) {
            return "实时天气：本平台未接入实时天气数据，无法提供气温、天气现象、降水、湿度等任何实时天气信息。"
                    + "游客问天气时，请如实说明查不到、建议其自行查看天气预报，绝不可以用常识估计或编造具体数值。";
        }
        WeatherSnapshot snap = weather.snapshot();   // 有 30 分钟缓存，命中时不发请求
        if (snap == null) {
            return "实时天气：平台已接入实时天气查询，但当前取数失败。"
                    + "游客问天气时，请调用天气工具重试；若仍失败则如实说明暂不可用，绝不可凭常识估计或编造具体数值。";
        }
        return "实时天气（和风天气实测数据，可直接引用，严禁再用常识改写或估计）：\n"
                + snap.toText()
                + "\n游客问及天气、出行适宜性时请以此为准；如需逐小时等更细数据可调用天气工具。";
    }

    /**
     * 在 {@link #platformDigest()} 之上，把与本次提问<b>语义最相关</b>的知识库正文一并注入。
     *
     * <p>为什么要这一步：摘要里只有「官方说明条目(标题)」，模型知道有《下雨天可以取消采摘吗？》
     * 这一条、却看不到它写了什么，于是会拿"常识"补细节——实测问「我临时去不了了怎么办」时，
     * 模型编出了"提前 24 小时免费取消、不足 24 小时扣 10% 手续费"，
     * 而库里真正的规则是"未核销前全额退回、到场临时取消按 80% 退款"。
     *
     * <p>为什么只注入"命中"的正文：这是"选择性"的核心。库里有几十条说明，
     * 全量喂进去既浪费上下文又稀释注意力；按语义只取最靠前的几篇，
     * 模型拿到的就是与本次问题真正相关的原文。阈值刻意比回答阈值低一档——
     * 给模型的参考资料，宁可多给一条不相关的，也好过该给没给、模型开始编造。
     *
     * <p>任何异常都只是退回「仅标题」的旧行为：接地是锦上添花，绝不能影响对话可用性。
     */
    public String platformDigest(String question) {
        String base = platformDigest();
        AiProperties.Embedding cfg = aiProperties.getEmbedding();
        if (!cfg.isGroundingEnabled() || !StringUtils.hasText(question)) {
            return base;
        }
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        if (index == null) {
            return base;
        }
        List<VectorHit> hits;
        try {
            hits = index.search(question, cfg.getGroundingTopK(), cfg.getGroundingMinScore());
        } catch (Exception e) {
            // 接地失败不影响主链路，退回旧行为
            return base;
        }
        if (hits.isEmpty()) {
            return base;
        }
        log.debug("知识库接地：注入 {} 条命中正文（阈值 {}）-> {}", hits.size(), cfg.getGroundingMinScore(),
                hits.stream().map(VectorHit::title).collect(Collectors.joining("、")));
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n【官方说明原文·与本次提问最相关，请严格据此回答】\n");
        for (VectorHit hit : hits) {
            sb.append("《").append(nz(hit.title())).append("》")
                    .append(clip(nz(hit.content()), cfg.getGroundingMaxChars())).append('\n');
        }
        sb.append("以上原文之外的时间、金额、比例、规则等细节一律不要自行补充，");
        sb.append("原文未覆盖时请如实说明，并建议游客咨询在线客服核实。");
        return sb.toString();
    }

    /** 按字数截断，超出部分用省略号收尾——防止将来出现超长文档撑爆上下文 */
    private static String clip(String text, int max) {
        if (text == null || max <= 0 || text.length() <= max) {
            return nz(text);
        }
        return text.substring(0, max) + "…";
    }

    // ==================== 中文 2-gram 相似度 ====================

    /** 去标点后切 2-gram：中文没有天然分词，2-gram 重叠是轻量且稳健的相关度信号 */
    public static Set<String> ngrams(String text) {
        Set<String> grams = new HashSet<>();
        String s = nz(text).replaceAll("[\\s\\p{Punct}，。、；：？！「」『』（）《》·—…]", "");
        if (s.length() < 2) {
            if (!s.isEmpty()) {
                grams.add(s);
            }
            return grams;
        }
        for (int i = 0; i + 2 <= s.length(); i++) {
            grams.add(s.substring(i, i + 2));
        }
        return grams;
    }

    public static int overlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String g : a) {
            if (b.contains(g)) {
                count++;
            }
        }
        return count;
    }

    // ==================== 工具方法 ====================

    public String routePublicId(TravelRoute r) {
        return publicId(r.getCode(), r.getId());
    }

    /** C 端历史链接使用 scenic-1 形式编码，无编码时退回主键 */
    public String publicId(String code, Long id) {
        return StringUtils.hasText(code) ? code : String.valueOf(id);
    }

    public String price(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    public String rating(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    public String sales(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    /** 场次剩余名额 */
    public int remain(PickingSession s) {
        int max = s.getMaxPeople() == null ? 0 : s.getMaxPeople();
        int booked = s.getBookedCount() == null ? 0 : s.getBookedCount();
        return Math.max(max - booked, 0);
    }

    public static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    public static String trim(String value, int max) {
        String s = nz(value);
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static boolean is(Integer flag) {
        return flag != null && flag == 1;
    }
}

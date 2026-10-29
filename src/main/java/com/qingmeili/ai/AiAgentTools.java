package com.qingmeili.ai;

import com.qingmeili.ai.embedding.KbVectorIndex;
import com.qingmeili.ai.embedding.VectorHit;
import com.qingmeili.ai.weather.WeatherClient;
import com.qingmeili.ai.weather.WeatherSnapshot;
import com.qingmeili.entity.*;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 山野向导 Agent 的工具集（Function Calling）。
 *
 * <p>大模型本身并不知道「青梅里」有哪些民宿、哪款梅酒在卖。这里把平台真实数据包装成工具，
 * 由模型自行决定调用哪个、传什么参数，再用返回的事实组织回答——这样既能保持对话的灵活性，
 * 又能保证答案有据可查，不靠模型编造。
 *
 * <p>所有工具都只返回「文本摘要 + 真实主键(id)」，主键随后用于回填 C 端推荐卡片。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAgentTools {

    private final AiCardFactory data;

    /**
     * 向量检索器。用 {@code ObjectProvider} 而不是直接注入：
     * 向量检索可能因为未配置 Key、嵌入服务不可用、向量表未建等原因整个 Bean 不存在，
     * 此时工具应当静默回落到关键词检索，而不是让 Agent 因为缺少依赖起不来。
     */
    private final ObjectProvider<KbVectorIndex> vectorIndexProvider;

    /**
     * 天气数据源。同样用 {@code ObjectProvider}：未配置 API Host 时这个 Bean 根本不存在，
     * 工具应当如实告知「查不了天气」，而不是让 Agent 起不来，更不能对着游客编天气。
     */
    private final ObjectProvider<WeatherClient> weatherProvider;

    @Tool("查询平台在售的乡村好物/农产品。keyword 传商品关键词（如 青梅酒、蜂蜜、果干、礼盒），传空字符串表示不限。返回商品 id、名称、价格与简介。")
    public String searchProducts(@P("商品关键词，可为空") String keyword) {
        List<Product> list = data.products(8, StringUtils.hasText(keyword) ? keyword.trim() : null);
        if (list.isEmpty()) {
            return "没有找到匹配的在售商品。";
        }
        return list.stream().map(p -> row(
                "商品", p.getId(), p.getName(),
                "价格", "¥" + data.price(p.getPrice()),
                "已售", data.sales(p.getSales()),
                "简介", AiCardFactory.trim(AiCardFactory.nz(p.getSummary()), 40))).collect(Collectors.joining("\n"));
    }

    @Tool("查询平台可预订的民宿，按评分从高到低。minRating 传最低评分（传 0 表示不限）。返回民宿 id、名称、评分、地址与简介。")
    public String listHomestays(@P("最低评分，0 表示不限") double minRating) {
        List<Homestay> list = data.homestays(8).stream()
                .filter(h -> minRating <= 0 || (h.getRating() != null && h.getRating().doubleValue() >= minRating))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return "当前没有符合评分要求的可预订民宿。";
        }
        return list.stream().map(h -> row(
                "民宿", h.getId(), h.getName(),
                "评分", data.rating(h.getRating()),
                "地址", AiCardFactory.nz(h.getAddress()),
                "简介", AiCardFactory.trim(AiCardFactory.nz(h.getIntro()), 40))).collect(Collectors.joining("\n"));
    }

    @Tool("查询平台全部在营景点。返回景点 id、名称、评分、距村口距离与简介。")
    public String listScenics() {
        List<Scenic> list = data.scenics(8);
        if (list.isEmpty()) {
            return "当前没有在营景点。";
        }
        return list.stream().map(s -> row(
                "景点", s.getId(), s.getName(),
                "评分", data.rating(s.getRating()),
                "位置", AiCardFactory.nz(s.getLocation()),
                "距离", AiCardFactory.nz(s.getDistance()),
                "简介", AiCardFactory.trim(AiCardFactory.nz(s.getSummary()), 40))).collect(Collectors.joining("\n"));
    }

    @Tool("查询平台推荐的乡村旅行路线。theme 传主题（亲子 / 休闲 / 研学），传空字符串表示不限。返回路线 id、标题、时长、难度、最佳季节与简介。")
    public String listRoutes(@P("路线主题，可为空") String theme) {
        List<TravelRoute> all = data.routes(12);
        List<TravelRoute> list = all;
        if (StringUtils.hasText(theme)) {
            String t = theme.trim();
            List<TravelRoute> filtered = all.stream().filter(r -> {
                String text = AiCardFactory.nz(r.getTitle()) + AiCardFactory.nz(r.getSubtitle())
                        + AiCardFactory.nz(r.getSummary()) + AiCardFactory.nz(r.getDetail());
                return text.contains(t)
                        || ("亲子".equals(t) && (text.contains("研学") || text.contains("孩子")))
                        || ("休闲".equals(t) && (text.contains("轻氧") || text.contains("缓坡") || text.contains("慢")));
            }).collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                list = filtered;
            }
        }
        if (list.isEmpty()) {
            return "当前没有上架的推荐路线。";
        }
        return list.stream().map(r -> row(
                "路线", r.getId(), r.getTitle(),
                "时长", AiCardFactory.nz(r.getDuration()),
                "难度", AiCardFactory.nz(r.getDifficulty()),
                "最佳季节", AiCardFactory.nz(r.getBestTime()),
                "简介", AiCardFactory.trim(AiCardFactory.nz(r.getSummary()), 46))).collect(Collectors.joining("\n"));
    }

    @Tool("查询青梅采摘园及其最近可预约场次。返回果园 id、名称、最近场次的日期时段、价格、剩余名额与简介。")
    public String listPickingSessions() {
        List<Orchard> orchards = data.orchards(6);
        if (orchards.isEmpty()) {
            return "当前没有开放预约的采摘园。";
        }
        Map<Long, PickingSession> earliest = data.earliestSessions();
        return orchards.stream().map(o -> {
            PickingSession s = earliest.get(o.getId());
            String slot = s == null ? "暂无可约场次"
                    : s.getSlotDate().format(AiCardFactory.MMDD) + " " + AiCardFactory.nz(s.getTimeRange())
                        + "，¥" + data.price(s.getPrice()) + "/人，余 " + data.remain(s) + " 位";
            return row("采摘园", o.getId(), o.getName(),
                    "最近场次", slot,
                    "地址", AiCardFactory.nz(o.getAddress()),
                    "简介", AiCardFactory.trim(AiCardFactory.nz(o.getIntro()), 40));
        }).collect(Collectors.joining("\n"));
    }

    @Tool("检索平台知识库。用于回答平台规则、门票与退改说明、采摘须知、交通停车、活动介绍等官方说明类问题。query 一般直接传游客的原话；采用语义检索，游客用口语化的说法（如「临时去不了」）也能找到对应的官方条款，不必强行改写成关键词。")
    public String searchKnowledgeBase(@P("检索关键词或游客原话") String query) {
        if (!StringUtils.hasText(query)) {
            return "知识库暂无可用条目。";
        }
        // 首选：向量语义检索（标题片 + 正文片，余弦相似度 + 关键词融合）
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        if (index != null && index.available()) {
            List<VectorHit> hits = index.search(query);
            if (!hits.isEmpty()) {
                return hits.stream()
                        .map(hit -> "[知识库] 标题：" + AiCardFactory.nz(hit.title())
                                + "｜分类：" + AiCardFactory.nz(hit.category())
                                + "｜正文：" + AiCardFactory.trim(AiCardFactory.nz(hit.content()), 220))
                        .collect(Collectors.joining("\n"));
            }
            // 语义检索通了但没有过阈值的条目，说明确实没有相关官方说明，
            // 不再退回关键词——避免低质量的字面命中把不相关内容塞给模型
            return "知识库中没有与该问题相关的官方说明条目。";
        }
        // 兜底：向量检索不可用（未配置 Key / 嵌入服务故障 / 向量表未建）时，退回 2-gram 关键词检索
        return keywordSearch(query);
    }

    /**
     * 原始的 2-gram 关键词检索，作为向量通道不可用时的降级路径保留。
     *
     * <p>它没有语义理解能力（问"买了不想要了能退吗"能靠"退"字命中"退改说明"，
     * 但问"我临时去不了了怎么办"就大概率落空），胜在零依赖、零成本、断网可用。
     */
    private String keywordSearch(String query) {
        List<KbDocument> docs = data.knowledgeDocuments();
        if (docs.isEmpty()) {
            return "知识库暂无可用条目。";
        }
        Set<String> qGrams = AiCardFactory.ngrams(query);
        List<KbDocument> top = docs.stream()
                .sorted(Comparator.comparingInt((KbDocument d) -> {
                    String title = AiCardFactory.nz(d.getTitle());
                    boolean hit = title.length() > 1 && AiCardFactory.nz(query).contains(title);
                    return (hit ? 8 : 0)
                            + AiCardFactory.overlap(qGrams, AiCardFactory.ngrams(title)) * 3
                            + AiCardFactory.overlap(qGrams, AiCardFactory.ngrams(AiCardFactory.nz(d.getCategory()))) * 2
                            + Math.min(AiCardFactory.overlap(qGrams, AiCardFactory.ngrams(AiCardFactory.nz(d.getContent()))), 3);
                }).reversed())
                .limit(3)
                .collect(Collectors.toList());
        return top.stream().map(d -> "[知识库] 标题：" + AiCardFactory.nz(d.getTitle())
                        + "｜分类：" + AiCardFactory.nz(d.getCategory())
                        + "｜正文：" + AiCardFactory.trim(AiCardFactory.nz(d.getContent()), 220))
                .collect(Collectors.joining("\n"));
    }

    @Tool("查询平台整体资源概况：民宿、商品、景点、路线、采摘园各自的数量与代表名称。回答「你们这里有什么」这类泛问时先调用它。")
    public String platformOverview() {
        // 村域平台体量很小，一次取全量用于计数，另只展示前 3 个代表名称
        List<Homestay> homestays = data.homestays(500);
        List<Product> products = data.products(500, null);
        List<Scenic> scenics = data.scenics(500);
        List<TravelRoute> routes = data.routes(500);
        List<Orchard> orchards = data.orchards(500);
        StringBuilder sb = new StringBuilder();
        sb.append("民宿 ").append(homestays.size()).append(" 家：")
                .append(names(homestays, Homestay::getName)).append('\n');
        sb.append("在售好物 ").append(products.size()).append(" 款：")
                .append(names(products, Product::getName)).append('\n');
        sb.append("景点 ").append(scenics.size()).append(" 处：")
                .append(names(scenics, Scenic::getName)).append('\n');
        sb.append("推荐路线 ").append(routes.size()).append(" 条：")
                .append(names(routes, TravelRoute::getTitle)).append('\n');
        sb.append("采摘园 ").append(orchards.size()).append(" 个：")
                .append(names(orchards, Orchard::getName));
        return sb.toString();
    }

    @Tool("查询青梅里园区所在地的实时天气和未来三天预报。仅当问题确实与天气或出行适宜性相关时才调用，例如"
            + "「今天天气怎么样」「下雨天还能采摘吗」「这周末适合来吗」「要带伞吗」。"
            + "返回实况天气（天气现象、气温、体感、湿度、风力）与未来三天预报。"
            + "调用前不要凭常识猜测天气，一律以本工具返回的数据为准。")
    public String getWeather() {
        WeatherClient client = weatherProvider.getIfAvailable();
        if (client == null || !client.available()) {
            log.info("[工具] getWeather 被调用，但天气服务未配置");
            return "天气服务未配置，无法查询实时天气。请告知游客暂无法提供实时天气，"
                    + "只回答采摘规则等已知信息，不要编造天气数据。";
        }
        WeatherSnapshot snapshot = client.snapshot();
        if (snapshot == null) {
            log.warn("[工具] getWeather 被调用，但取数失败（返回 null）");
            return "天气服务暂时不可用。请告知游客稍后再试，不要编造天气数据。";
        }
        log.info("[工具] getWeather 被调用，实况 {} {}℃", snapshot.text(), snapshot.temp());
        return snapshot.toText();
    }

    /** 统一的工具返回行格式：类型 id=1｜名称=xxx｜字段=值… */
    private String row(String type, Long id, String name, String... pairs) {
        StringBuilder sb = new StringBuilder(type).append(' ').append("id=").append(id)
                .append("｜名称=").append(AiCardFactory.nz(name));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String value = AiCardFactory.nz(pairs[i + 1]);
            if (!value.isEmpty()) {
                sb.append("｜").append(pairs[i]).append('=').append(value);
            }
        }
        return sb.toString();
    }

    /** 概况里只列举前 3 个代表名称，控制 token 消耗 */
    private <T> String names(List<T> list, java.util.function.Function<T, String> nameGetter) {
        if (list.isEmpty()) {
            return "无";
        }
        return list.stream().limit(3).map(nameGetter)
                .filter(StringUtils::hasText).collect(Collectors.joining("、"));
    }
}

package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qingmeili.common.result.Result;
import com.qingmeili.common.util.ContentStore;
import com.qingmeili.entity.Homestay;
import com.qingmeili.entity.Orchard;
import com.qingmeili.entity.Scenic;
import com.qingmeili.entity.TravelRoute;
import com.qingmeili.mapper.HomestayMapper;
import com.qingmeili.mapper.OrchardMapper;
import com.qingmeili.mapper.ScenicMapper;
import com.qingmeili.mapper.TravelRouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C端 - 旅行内容（景点 / 路线 / 导览图 / 地图点位，公开接口）。
 * 景点与路线读取 scenic / travel_route 表，后台编辑后 C 端即时生效；
 * 导览图为美术编辑数据，仍由 classpath JSON 下发。
 */
@RestController
@RequestMapping("/api/travel")
@RequiredArgsConstructor
public class TravelContentController {

    /** 静态导览点位已下线、由数据库实体承接时的名称对应关系 */
    private static final Map<String, String> SPOT_ALIASES = Map.of(
            "溪畔小院", "溪畔人家",
            "溪谷梅园", "青梅生态园",
            "青梅半山果园", "梅岭果园",
            "山风梅舍", "梅语山居");

    /** 下发给 C 端的文案（路线停靠点、行程标题、亮点/含项）用的替换表，长名在前避免被短名抢先 */
    private static final String[][] SPOT_TEXT_FIXES = {
            {"青梅半山果园", "梅岭果园"},
            {"半山果园", "梅岭果园"},
            {"溪畔小院", "溪畔人家"},
            {"溪谷梅园", "青梅生态园"},
            {"山风梅舍", "梅语山居"}};

    private final ContentStore contentStore;
    private final ScenicMapper scenicMapper;
    private final TravelRouteMapper travelRouteMapper;
    private final OrchardMapper orchardMapper;
    private final HomestayMapper homestayMapper;
    private final ObjectMapper objectMapper;

    @GetMapping("/scenic")
    public Result<JsonNode> scenicList() {
        ArrayNode array = objectMapper.createArrayNode();
        scenics().forEach(s -> array.add(scenicNode(s)));
        return Result.ok(array);
    }

    @GetMapping("/scenic/{id}")
    public Result<JsonNode> scenicDetail(@PathVariable String id) {
        Scenic scenic = findScenic(id);
        return scenic == null ? Result.fail("景点不存在") : Result.ok(scenicNode(scenic));
    }

    @GetMapping("/routes")
    public Result<JsonNode> routeList() {
        ArrayNode array = objectMapper.createArrayNode();
        routes().forEach(r -> array.add(routeNode(r)));
        return Result.ok(array);
    }

    @GetMapping("/routes/{id}")
    public Result<JsonNode> routeDetail(@PathVariable String id) {
        TravelRoute route = findRoute(id);
        return route == null ? Result.fail("路线不存在") : Result.ok(routeNode(route));
    }

    @GetMapping("/guide")
    public Result<JsonNode> guide() {
        JsonNode base = contentStore.getGuide();
        ObjectNode node = base.isObject()
                ? (ObjectNode) base.deepCopy()
                : objectMapper.createObjectNode();
        ArrayNode spots = mergedSpots();
        node.set("spots", spots);
        renumberCodesAndRoutes(node, spots);
        normalizeSpotNames(node);
        return Result.ok(node);
    }

    /**
     * 库里路线文案沿用过导览图上的旧点位名（山风梅舍 / 溪畔小院 / 溪谷梅园 / 半山果园），
     * 这些名字在合并后的 16 个点位的导览图里并不存在，用户按文案找不到、也订不到。
     * 下发前统一翻译成数据库实体名，文案与图上的编号才指向同一个地方。
     */
    private void normalizeSpotNames(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fields = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                JsonNode child = obj.get(field);
                if (child.isTextual()) {
                    String fixed = renameSpotsIn(child.asText());
                    if (!fixed.equals(child.asText())) {
                        obj.put(field, fixed);
                    }
                } else {
                    normalizeSpotNames(child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                if (child.isTextual()) {
                    String fixed = renameSpotsIn(child.asText());
                    if (!fixed.equals(child.asText())) {
                        arr.set(i, objectMapper.getNodeFactory().textNode(fixed));
                    }
                } else {
                    normalizeSpotNames(child);
                }
            }
        }
    }

    private String renameSpotsIn(String text) {
        String out = text;
        for (String[] fix : SPOT_TEXT_FIXES) {
            out = out.replace(fix[0], fix[1]);
        }
        return out;
    }

    /**
     * 静态导览 JSON 与数据库点位共用一套 code 编号：合并后统一重排，
     * 并把路线 viaCodes 从静态编号翻译成合并后的编号，避免同编号指向多个点位。
     */
    private void renumberCodesAndRoutes(ObjectNode node, ArrayNode spots) {
        Map<String, String> nameToCode = new LinkedHashMap<>();
        int seq = 1;
        for (JsonNode spot : spots) {
            String code = String.valueOf(seq++);
            ((ObjectNode) spot).put("code", code);
            nameToCode.put(spot.get("name").asText(), code);
        }
        Map<String, String> staticCodeToName = new HashMap<>();
        JsonNode staticSpots = contentStore.getGuide().get("spots");
        if (staticSpots != null && staticSpots.isArray()) {
            for (JsonNode spot : staticSpots) {
                staticCodeToName.put(spot.get("code").asText(), spot.get("name").asText());
            }
        }
        JsonNode routes = node.get("routes");
        if (routes == null || !routes.isArray()) {
            return;
        }
        for (JsonNode route : routes) {
            JsonNode via = route.get("viaCodes");
            ArrayNode mapped = objectMapper.createArrayNode();
            Set<String> seen = new HashSet<>();
            if (via != null && via.isArray()) {
                for (JsonNode old : via) {
                    String name = staticCodeToName.get(old.asText());
                    if (name == null) {
                        continue;
                    }
                    String target = SPOT_ALIASES.getOrDefault(name, name);
                    String code = nameToCode.get(target);
                    if (code != null && seen.add(code)) {
                        mapped.add(code);
                    }
                }
            }
            ((ObjectNode) route).set("viaCodes", mapped);
        }
    }

    /**
     * 地图点位：景点/果园/民宿读数据库（后台编辑坐标与上下架即时生效），
     * 工坊与餐饮为美术编辑点位，仍取自导览 JSON。
     */
    @GetMapping("/map-markers")
    public Result<JsonNode> mapMarkers(@RequestParam(required = false) String category) {
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode spot : mergedSpots()) {
            String cat = spot.has("category") ? spot.get("category").asText() : "";
            if (category != null && !category.isEmpty() && !category.equals(cat)) {
                continue;
            }
            ObjectNode marker = objectMapper.createObjectNode();
            marker.put("id", spot.get("id").asText());
            marker.put("category", cat);
            marker.put("name", spot.has("name") ? spot.get("name").asText() : "");
            marker.put("image", spot.has("image") ? spot.get("image").asText() : "");
            marker.put("distance", spot.has("distance") ? spot.get("distance").asText() : "");
            marker.put("summary", spot.has("summary") ? spot.get("summary").asText() : "");
            marker.put("open", spot.has("open") && spot.get("open").asBoolean());
            marker.put("bookable", spot.has("bookable") && spot.get("bookable").asBoolean());
            ObjectNode location = objectMapper.createObjectNode();
            location.put("lat", spot.has("lat") ? spot.get("lat").asDouble() : 0);
            location.put("lng", spot.has("lng") ? spot.get("lng").asDouble() : 0);
            marker.set("location", location);
            result.add(marker);
        }
        return Result.ok(result);
    }

    /** 导览画布坐标与经纬度的换算关系，与历史静态点位保持一致 */
    private double toX(Double lng) {
        return clamp((lng - 103.58) / 0.0004, 60, 720);
    }

    private double toY(Double lat) {
        return clamp((30.78 - lat) / 0.0004, 100, 840);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ArrayNode mergedSpots() {
        ArrayNode spots = objectMapper.createArrayNode();
        scenicMapper.selectList(new LambdaQueryWrapper<Scenic>()
                        .eq(Scenic::getStatus, 1)
                        .orderByAsc(Scenic::getSort).orderByAsc(Scenic::getId))
                .forEach(s -> {
                    if (s.getLng() == null || s.getLat() == null) {
                        return;
                    }
                    ObjectNode spot = spots.addObject();
                    spot.put("id", publicId(s.getCode(), s.getId()));
                    spot.put("code", String.valueOf(s.getId()));
                    spot.put("name", nz(s.getName()));
                    spot.put("nameEn", nz(s.getNameEn()));
                    spot.put("category", "scenic");
                    spot.put("x", toX(s.getLng().doubleValue()));
                    spot.put("y", toY(s.getLat().doubleValue()));
                    spot.put("lat", s.getLat().doubleValue());
                    spot.put("lng", s.getLng().doubleValue());
                    spot.put("image", nz(s.getCoverImage()));
                    spot.put("summary", StringUtils.hasText(s.getSummary()) ? s.getSummary() : nz(s.getIntro()));
                    spot.put("summaryEn", nz(s.getSummaryEn()));
                    spot.put("distance", nz(s.getDistance()));
                    spot.put("open", true);
                    spot.put("bookable", false);
                    spot.put("service", nz(s.getLocation()));
                });
        orchardMapper.selectList(new LambdaQueryWrapper<Orchard>().eq(Orchard::getStatus, 1))
                .forEach(o -> {
                    if (o.getLng() == null || o.getLat() == null) {
                        return;
                    }
                    ObjectNode spot = spots.addObject();
                    spot.put("id", "orchard-" + o.getId());
                    spot.put("code", String.valueOf(o.getId()));
                    spot.put("name", nz(o.getName()));
                    spot.put("nameEn", nz(o.getNameEn()));
                    spot.put("category", "picking");
                    spot.put("x", toX(o.getLng().doubleValue()));
                    spot.put("y", toY(o.getLat().doubleValue()));
                    spot.put("lat", o.getLat().doubleValue());
                    spot.put("lng", o.getLng().doubleValue());
                    spot.put("image", nz(o.getCoverImage()));
                    spot.put("summary", nz(o.getIntro()));
                    spot.put("summaryEn", nz(o.getSummaryEn()));
                    spot.put("distance", nz(o.getAddress()));
                    spot.put("open", true);
                    spot.put("bookable", true);
                    spot.put("service", "采摘预约");
                });
        homestayMapper.selectList(new LambdaQueryWrapper<Homestay>().eq(Homestay::getStatus, 1))
                .forEach(h -> {
                    if (h.getLng() == null || h.getLat() == null) {
                        return;
                    }
                    ObjectNode spot = spots.addObject();
                    spot.put("id", "homestay-" + h.getId());
                    spot.put("code", String.valueOf(h.getId()));
                    spot.put("name", nz(h.getName()));
                    spot.put("nameEn", nz(h.getNameEn()));
                    spot.put("category", "homestay");
                    spot.put("x", toX(h.getLng().doubleValue()));
                    spot.put("y", toY(h.getLat().doubleValue()));
                    spot.put("lat", h.getLat().doubleValue());
                    spot.put("lng", h.getLng().doubleValue());
                    spot.put("image", nz(h.getCoverImage()));
                    spot.put("summary", nz(h.getIntro()));
                    spot.put("summaryEn", nz(h.getSummaryEn()));
                    spot.put("distance", nz(h.getAddress()));
                    spot.put("open", true);
                    spot.put("bookable", true);
                    spot.put("service", "民宿预订");
                });
        JsonNode staticSpots = contentStore.getGuide().get("spots");
        if (staticSpots != null && staticSpots.isArray()) {
            for (JsonNode spot : staticSpots) {
                String cat = spot.has("category") ? spot.get("category").asText() : "";
                if ("craft".equals(cat) || "food".equals(cat)) {
                    spots.add(spot.deepCopy());
                }
            }
        }
        return spots;
    }

    private List<Scenic> scenics() {
        return scenicMapper.selectList(new LambdaQueryWrapper<Scenic>()
                .eq(Scenic::getStatus, 1)
                .orderByAsc(Scenic::getSort)
                .orderByAsc(Scenic::getId));
    }

    /** C 端历史链接使用 scenic-1 形式编码，同时兼容主键 */
    private Scenic findScenic(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        Scenic byCode = scenicMapper.selectOne(new LambdaQueryWrapper<Scenic>()
                .eq(Scenic::getCode, id).eq(Scenic::getStatus, 1).last("limit 1"));
        if (byCode != null) {
            return byCode;
        }
        if (id.matches("\\d+")) {
            return scenicMapper.selectById(Long.valueOf(id));
        }
        return null;
    }

    private List<TravelRoute> routes() {
        return travelRouteMapper.selectList(new LambdaQueryWrapper<TravelRoute>()
                .eq(TravelRoute::getStatus, 1)
                .orderByAsc(TravelRoute::getSort)
                .orderByAsc(TravelRoute::getId));
    }

    private TravelRoute findRoute(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        TravelRoute byCode = travelRouteMapper.selectOne(new LambdaQueryWrapper<TravelRoute>()
                .eq(TravelRoute::getCode, id).eq(TravelRoute::getStatus, 1).last("limit 1"));
        if (byCode != null) {
            return byCode;
        }
        if (id.matches("\\d+")) {
            return travelRouteMapper.selectById(Long.valueOf(id));
        }
        return null;
    }

    private ObjectNode scenicNode(Scenic s) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", publicId(s.getCode(), s.getId()));
        // 收藏表 target_id 是 bigint，而对外 id 可能是 scenic-1 这类编码，
        // 单独回传主键供 C 端写收藏，避免前端从编码里截数字截错。
        node.put("dbId", s.getId());
        node.put("name", nz(s.getName()));
        node.put("nameEn", nz(s.getNameEn()));
        node.put("phone", nz(s.getPhone()));
        node.put("lat", s.getLat() == null ? 0 : s.getLat().doubleValue());
        node.put("lng", s.getLng() == null ? 0 : s.getLng().doubleValue());
        node.put("image", nz(s.getCoverImage()));
        node.put("location", nz(s.getLocation()));
        node.put("distance", nz(s.getDistance()));
        node.put("rating", s.getRating() == null ? 5.0 : s.getRating().doubleValue());
        node.set("tags", stringArray(splitTags(s.getTags())));
        node.put("summary", StringUtils.hasText(s.getSummary()) ? s.getSummary() : nz(s.getIntro()));
        node.put("summaryEn", nz(s.getSummaryEn()));
        node.put("openTime", nz(s.getOpenTime()));
        node.put("audioUrl", nz(s.getAudioUrl()));
        node.put("audioText", nz(s.getAudioText()));
        node.put("audioUrlEn", nz(s.getAudioUrlEn()));
        node.put("audioTextEn", nz(s.getAudioTextEn()));
        node.put("intro", nz(s.getIntro()));
        node.put("category", nz(s.getCategory()));
        return node;
    }

    private ObjectNode routeNode(TravelRoute r) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", publicId(r.getCode(), r.getId()));
        node.put("dbId", r.getId());
        node.put("title", nz(r.getTitle()));
        node.put("titleEn", nz(r.getTitleEn()));
        node.put("summaryEn", nz(r.getSummaryEn()));
        node.put("subtitle", nz(r.getSubtitle()));
        node.put("image", nz(r.getCoverImage()));
        node.put("duration", nz(r.getDuration()));
        node.put("distance", nz(r.getDistance()));
        node.put("difficulty", nz(r.getDifficulty()));
        node.put("bestTime", nz(r.getBestTime()));
        node.put("summary", nz(r.getSummary()));
        node.set("stops", stringArray(readStringList(r.getStops(), null)));

        JsonNode detail = parse(r.getDetail());
        node.set("suits", stringArray(readStringList(r.getDetail(), "suits")));
        node.set("highlights", stringArray(readStringList(r.getDetail(), "highlights")));
        node.set("includes", stringArray(readStringList(r.getDetail(), "includes")));
        node.set("tips", stringArray(readStringList(r.getDetail(), "tips")));
        ArrayNode itinerary = node.putArray("itinerary");
        JsonNode items = detail == null ? null : detail.get("itinerary");
        if (items != null && items.isArray()) {
            items.forEach(itinerary::add);
        }
        normalizeSpotNames(node);
        return node;
    }

    private String publicId(String code, Long id) {
        return StringUtils.hasText(code) ? code : String.valueOf(id);
    }

    /** tags 兼容 JSON 数组与逗号分隔两种历史写法 */
    private List<String> splitTags(String raw) {
        String value = nz(raw);
        if (value.isEmpty()) {
            return List.of();
        }
        if (value.startsWith("[")) {
            List<String> list = readStringList(value, null);
            if (!list.isEmpty()) {
                return list;
            }
        }
        List<String> list = new ArrayList<>();
        for (String part : value.split("[,，、]")) {
            if (StringUtils.hasText(part)) {
                list.add(part.trim());
            }
        }
        return list;
    }

    private List<String> readStringList(String json, String field) {
        JsonNode node = parse(json);
        if (node == null) {
            return List.of();
        }
        JsonNode target = field == null ? node : node.get(field);
        if (target == null || !target.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        target.forEach(item -> list.add(item.asText()));
        return list;
    }

    private JsonNode parse(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private ArrayNode stringArray(List<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}

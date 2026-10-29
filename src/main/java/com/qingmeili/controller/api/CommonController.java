package com.qingmeili.controller.api;

import com.qingmeili.common.result.Result;
import com.qingmeili.entity.SysConfig;
import com.qingmeili.service.AppearanceService;
import com.qingmeili.service.NoticeService;
import com.qingmeili.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * C端 - 公共接口
 */
@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final SysConfigService sysConfigService;
    private final NoticeService noticeService;
    private final AppearanceService appearanceService;

    /** 外观主题：新版前端启动时拉取，失败则用本地兜底 */
    @GetMapping("/appearance")
    public Result<Map<String, Object>> appearance() {
        return Result.ok(appearanceService.appearance());
    }

    /** 未登录访客可见的公告（ALL + USER） */
    @GetMapping("/notices")
    public Result<List<Map<String, Object>>> notices(@RequestParam(defaultValue = "6") int limit) {
        return Result.ok(noticeService.publicFeed(limit));
    }

    @GetMapping("/notices/{id}")
    public Result<Map<String, Object>> noticeDetail(@PathVariable Long id) {
        return Result.ok(noticeService.publicDetail(id));
    }

    @GetMapping("/configs")
    public Result<Map<String, String>> configs(@RequestParam(required = false) List<String> keys) {
        Map<String, String> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            // 未传 keys 时返回全部公开配置
            List<SysConfig> all = sysConfigService.list(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig>());
            for (SysConfig cfg : all) {
                result.put(cfg.getConfigKey(), cfg.getConfigValue());
            }
            return Result.ok(result);
        }
        for (String key : keys) {
            Result<String> value = sysConfigService.getValue(key);
            if (value.getCode() == 200 && value.getData() != null) {
                result.put(key, value.getData());
            }
        }
        return Result.ok(result);
    }

    /**
     * 本地占位图生成接口：/api/common/placeholder?size=400x300&text=青梅干&bg=4CAF50
     * 返回内联 SVG，永不依赖外网（替代 via.placeholder.com，避免外部占位服务不可达导致裂图）
     */
    @GetMapping(value = "/placeholder", produces = "image/svg+xml")
    public org.springframework.http.ResponseEntity<byte[]> placeholder(
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String bg,
            @RequestParam(required = false) String fg) {
        int width = 400, height = 300;
        if (size != null && size.matches("\\d+x\\d+")) {
            String[] p = size.toLowerCase().split("x");
            width = Math.max(1, Integer.parseInt(p[0]));
            height = Math.max(1, Integer.parseInt(p[1]));
        }
        String bgc = safeColor(bg, "#4CAF50");
        String fgc = safeColor(fg, "#FFFFFF");
        String label = (text == null || text.isBlank()) ? "青梅里" : escapeXml(text.length() > 20 ? text.substring(0, 20) : text);
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='" + width + "' height='" + height + "'>"
                + "<rect width='100%' height='100%' fill='" + bgc + "'/>"
                + "<rect width='100%' height='100%' fill='url(#g)' opacity='0.15'/>"
                + "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>"
                + "<stop offset='0' stop-color='#ffffff'/><stop offset='1' stop-color='#000000'/></linearGradient></defs>"
                + "<g fill='" + fgc + "' text-anchor='middle' font-family='Microsoft YaHei,Arial,sans-serif'>"
                + "<text x='50%' y='" + (height / 2 - 14) + "' font-size='" + Math.max(14, width / 14) + "' font-weight='bold'>"
                + escapeXml("青梅里 · " + label) + "</text>"
                + "<text x='50%' y='" + (height / 2 + 24) + "' font-size='" + Math.max(12, width / 22) + "' opacity='0.85'>"
                + width + "x" + height + "</text>"
                + "</g></svg>";
        byte[] body = svg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=86400")
                .body(body);
    }

    private String safeColor(String v, String def) {
        if (v != null && v.matches("[0-9a-fA-F]{6}")) {
            return "#" + v;
        }
        return def;
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @GetMapping("/region")
    public Result<List<Map<String, Object>>> region(@RequestParam(required = false) String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (parentId == null || parentId.isEmpty()) {
            Map<String, Object> sichuan = new HashMap<>();
            sichuan.put("id", "510000");
            sichuan.put("name", "四川省");
            sichuan.put("children", List.of(buildRegion("510100", "成都市", List.of(
                    buildRegion("510129", "大邑县", null),
                    buildRegion("510124", "郫都区", null),
                    buildRegion("510181", "都江堰市", null),
                    buildRegion("510116", "双流区", null),
                    buildRegion("510112", "龙泉驿区", null)
            ))));
            result.add(sichuan);
            return Result.ok(result);
        }
        result.addAll(switch (parentId) {
            case "510000" -> List.of(buildRegion("510100", "成都市", null));
            case "510100" -> List.of(
                    buildRegion("510129", "大邑县", null),
                    buildRegion("510124", "郫都区", null),
                    buildRegion("510181", "都江堰市", null),
                    buildRegion("510116", "双流区", null),
                    buildRegion("510112", "龙泉驿区", null));
            default -> List.of();
        });
        return Result.ok(result);
    }

    private Map<String, Object> buildRegion(String id, String name, List<Map<String, Object>> children) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("name", name);
        if (children != null) {
            item.put("children", children);
        }
        return item;
    }

    @GetMapping("/dicts")
    public Result<Map<String, List<Map<String, Object>>>> dicts(@RequestParam(required = false) String types) {
        Map<String, List<Map<String, Object>>> registry = new LinkedHashMap<>();

        List<Map<String, Object>> merchantTypes = new ArrayList<>();
        merchantTypes.add(buildDictItem(1, "果园采摘"));
        merchantTypes.add(buildDictItem(2, "民宿住宿"));
        merchantTypes.add(buildDictItem(3, "特产商品"));
        registry.put("merchantType", merchantTypes);
        registry.put("MerchantType", merchantTypes);

        List<Map<String, Object>> orderTypes = new ArrayList<>();
        orderTypes.add(buildDictItem(1, "采摘订单"));
        orderTypes.add(buildDictItem(2, "民宿订单"));
        orderTypes.add(buildDictItem(3, "商品订单"));
        registry.put("orderType", orderTypes);
        registry.put("OrderType", orderTypes);

        List<Map<String, Object>> orderStatuses = new ArrayList<>();
        orderStatuses.add(buildDictItem("pending_payment", "待支付"));
        orderStatuses.add(buildDictItem("pending_use", "待使用"));
        orderStatuses.add(buildDictItem("pending_delivery", "待发货"));
        orderStatuses.add(buildDictItem("shipped", "配送中"));
        orderStatuses.add(buildDictItem("completed", "已完成"));
        orderStatuses.add(buildDictItem("cancelled", "已取消"));
        orderStatuses.add(buildDictItem("refunding", "退款中"));
        orderStatuses.add(buildDictItem("refunded", "已退款"));
        orderStatuses.add(buildDictItem("refund_rejected", "退款被拒"));
        registry.put("orderStatus", orderStatuses);
        registry.put("OrderStatus", orderStatuses);

        List<Map<String, Object>> paymentMethods = new ArrayList<>();
        paymentMethods.add(buildDictItem("wechat", "微信支付"));
        paymentMethods.add(buildDictItem("alipay", "支付宝"));
        paymentMethods.add(buildDictItem("balance", "余额支付"));
        registry.put("PaymentMethod", paymentMethods);

        List<Map<String, Object>> auditStatuses = new ArrayList<>();
        auditStatuses.add(buildDictItem(0, "待审核"));
        auditStatuses.add(buildDictItem(1, "已通过"));
        auditStatuses.add(buildDictItem(2, "已拒绝"));
        registry.put("AuditStatus", auditStatuses);

        if (types == null || types.isBlank()) {
            return Result.ok(registry);
        }
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String t : types.split(",")) {
            String key = t.trim();
            if (registry.containsKey(key)) {
                result.put(key, registry.get(key));
            }
        }
        return Result.ok(result);
    }

    private Map<String, Object> buildDictItem(Object value, String label) {
        Map<String, Object> item = new HashMap<>();
        item.put("value", value);
        item.put("label", label);
        return item;
    }
}

package com.qingmeili.ai.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingmeili.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气（QWeather）数据源。
 *
 * <p><b>为什么用 JDK 自带的 {@code java.net.http.HttpClient} 而不是 OkHttp/RestTemplate：</b>
 * 这里只需要两个 GET 请求、解析一下 JSON。为它引一个 HTTP 客户端依赖，
 * 与便携版"解压即用、依赖越少越好"的原则相悖。
 *
 * <p><b>关于 API Host（踩过的坑）：</b>和风天气自 2025 年 4 月起用「API Host」取代了原来的公共域名，
 * 旧的 {@code devapi.qweather.com} 已于 2026-01-01 停服、{@code api.qweather.com} 于 2026-06-01 停服，
 * 继续用会直接返回 {@code 403 Invalid Host}。现在必须填控制台「设置」里那串专属地址
 * （形如 {@code abc1234xyz.re.qweatherapi.com}）。注意它和凭据 ID <b>不是</b>一回事。
 *
 * <p><b>缓存：</b>天气本身更新极慢——和风实况 10~40 分钟一次、预报一天才几次。
 * 不缓存的话，游客连问三遍"今天天气"就会烧三次额度。默认 30 分钟 TTL，
 * 与平台上已有的查询缓存思路一致。
 */
@Slf4j
public class QWeatherClient implements WeatherClient {

    private final String baseUrl;
    private final String key;
    private final String location;
    private final String label;
    private final boolean withForecast;
    private final long ttlMs;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json;

    private volatile WeatherSnapshot cache;
    private volatile long cacheAt;
    private volatile boolean failureLogged;

    public QWeatherClient(AiProperties.Weather cfg, ObjectMapper json) {
        String h = cfg.getHost() == null ? "" : cfg.getHost().trim();
        if (!h.isEmpty() && !h.startsWith("http://") && !h.startsWith("https://")) {
            h = "https://" + h;
        }
        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        this.baseUrl = h;
        this.key = cfg.getKey() == null ? "" : cfg.getKey().trim();
        this.location = cfg.getLocation();
        this.label = cfg.getLabel();
        this.withForecast = cfg.isForecast();
        this.ttlMs = Math.max(cfg.getCacheTtl().toMillis(), 0);
        this.timeout = cfg.getTimeout();
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public boolean available() {
        return !baseUrl.isEmpty() && !key.isEmpty();
    }

    @Override
    public String provider() {
        return "和风天气(QWeather)";
    }

    @Override
    public WeatherSnapshot snapshot() {
        WeatherSnapshot cached = this.cache;
        if (cached != null && System.currentTimeMillis() - cacheAt < ttlMs) {
            return cached;
        }
        try {
            WeatherSnapshot fresh = fetch();
            this.cache = fresh;
            this.cacheAt = System.currentTimeMillis();
            this.failureLogged = false;
            return fresh;
        } catch (Exception e) {
            // 只提醒一次，避免每次提问都刷日志
            if (!failureLogged) {
                log.warn("天气查询失败，本次降级（{}）：{}", provider(), e.toString());
                failureLogged = true;
            }
            // 过期缓存也比没有强：天气在半小时尺度上变化有限
            return cached;
        }
    }

    private WeatherSnapshot fetch() throws Exception {
        JsonNode nowRoot = get("/v7/weather/now");
        JsonNode now = nowRoot.path("now");

        List<WeatherSnapshot.Daily> daily = new ArrayList<>();
        if (withForecast) {
            for (JsonNode d : get("/v7/weather/3d").path("daily")) {
                daily.add(new WeatherSnapshot.Daily(
                        d.path("fxDate").asText(""),
                        d.path("textDay").asText(""),
                        d.path("textNight").asText(""),
                        d.path("tempMax").asText(""),
                        d.path("tempMin").asText(""),
                        d.path("precip").asText("")));
            }
        }
        return new WeatherSnapshot(
                label,
                nowRoot.path("updateTime").asText(""),
                now.path("text").asText(""),
                now.path("temp").asText(""),
                now.path("feelsLike").asText(""),
                now.path("humidity").asText(""),
                now.path("windDir").asText(""),
                now.path("windScale").asText(""),
                daily);
    }

    private JsonNode get(String path) throws Exception {
        String url = baseUrl + path + "?location=" + enc(location) + "&key=" + enc(key);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "qingmeili-backend")
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        String text = decode(resp);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " " + brief(text));
        }
        JsonNode node = json.readTree(text);
        String code = node.path("code").asText("");
        if (!"200".equals(code)) {
            throw new IllegalStateException("和风返回 code=" + code + " " + brief(text));
        }
        return node;
    }

    /** 未声明 Accept-Encoding 时和风也可能回 gzip，这里统一兜住，避免解析出乱码 */
    private static String decode(HttpResponse<byte[]> resp) throws IOException {
        byte[] body = resp.body() == null ? new byte[0] : resp.body();
        String enc = resp.headers().firstValue("content-encoding").orElse("");
        if (enc.toLowerCase().contains("gzip")) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
                body = in.readAllBytes();
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String enc(String s) {
        // 逗号保留原样：和风的经纬度写法就是 "经度,纬度"，编码成 %2C 虽合法但没必要
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("%2C", ",");
    }

    private static String brief(String s) {
        String one = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return one.length() > 120 ? one.substring(0, 120) + "…" : one;
    }
}

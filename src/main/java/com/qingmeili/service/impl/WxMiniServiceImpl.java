package com.qingmeili.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingmeili.service.SysConfigService;
import com.qingmeili.service.WxMiniService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序：登录 code 换 openid + 订阅消息下发。
 * 所有网络异常都吞掉记日志——通知失败不能阻断发货/退款/核销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxMiniServiceImpl implements WxMiniService {

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${wechat.miniapp.appid:}")
    private String appid;

    @Value("${wechat.miniapp.secret:}")
    private String secret;

    private volatile String accessToken;
    private volatile long tokenExpireAt;

    @Override
    public boolean configured() {
        return appid != null && !appid.isBlank() && secret != null && !secret.isBlank();
    }

    @Override
    public String code2Openid(String jsCode) {
        if (!configured() || jsCode == null || jsCode.isBlank()) {
            return null;
        }
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appid
                    + "&secret=" + secret + "&js_code=" + URLEncoder.encode(jsCode, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";
            JsonNode node = get(url);
            if (node != null && node.hasNonNull("openid")) {
                return node.get("openid").asText();
            }
            log.warn("wx jscode2session 失败: {}", node);
        } catch (Exception e) {
            log.warn("wx jscode2session 异常: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public boolean sendSubscribe(String openid, String templateKey, String thing, String remark, String page) {
        if (!configured() || openid == null || openid.isBlank()) {
            return false;
        }
        String templateId = configValue("wx.template." + templateKey);
        if (templateId == null || templateId.isBlank()) {
            log.info("订阅消息未配置模板 wx.template.{}，跳过下发", templateKey);
            return false;
        }
        try {
            String token = accessToken();
            if (token == null) {
                return false;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("touser", openid);
            body.put("template_id", templateId);
            body.put("page", page == null ? "pages/user/messages" : page);
            Map<String, Object> data = new LinkedHashMap<>();
            if (thing != null) {
                data.put("thing1", Map.of("value", truncate(thing, 20)));
            }
            if (remark != null) {
                data.put("thing2", Map.of("value", truncate(remark, 20)));
            }
            data.put("time3", Map.of("value", java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
            body.put("data", data);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + token))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(res.body());
            int errcode = node.path("errcode").asInt(0);
            if (errcode != 0) {
                log.info("订阅消息未送达 openid={} errcode={} msg={}", openid, errcode, node.path("errmsg").asText());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("订阅消息发送异常: {}", e.getMessage());
            return false;
        }
    }

    private synchronized String accessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return accessToken;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + appid
                + "&secret=" + secret;
        JsonNode node = get(url);
        if (node == null || !node.hasNonNull("access_token")) {
            log.warn("获取 access_token 失败: {}", node);
            return null;
        }
        accessToken = node.get("access_token").asText();
        tokenExpireAt = System.currentTimeMillis() + (node.path("expires_in").asLong(7200) - 300) * 1000;
        return accessToken;
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(res.body());
    }

    private String configValue(String key) {
        try {
            return sysConfigService.getValue(key).getData();
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}

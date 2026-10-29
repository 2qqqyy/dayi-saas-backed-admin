package com.qingmeili.service.impl;

import com.qingmeili.service.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 短信通道实现：demo 只打日志，webhook 走配置的网关。
 * 发送失败只记录日志，不阻断登录/注册主流程。
 */
@Slf4j
@Service
public class SmsSenderImpl implements SmsSender {

    @Value("${sms.provider:demo}")
    private String provider;

    @Value("${sms.webhook-url:}")
    private String webhookUrl;

    @Value("${sms.webhook-token:}")
    private String webhookToken;

    @Value("${sms.sign-name:青梅里}")
    private String signName;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public boolean sendCode(String mobile, String code) {
        if (!"webhook".equalsIgnoreCase(provider) || webhookUrl == null || webhookUrl.isBlank()) {
            log.info("【短信·演示】mobile={}, code={}（配置 sms.provider=webhook 后走真实网关）", mobile, code);
            return true;
        }
        String payload = String.format(
                "{\"mobile\":\"%s\",\"code\":\"%s\",\"signName\":\"%s\",\"template\":\"login\"}",
                mobile, code, signName);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (webhookToken != null && !webhookToken.isBlank()) {
                builder.header("Authorization", "Bearer " + webhookToken);
            }
            HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = res.statusCode() >= 200 && res.statusCode() < 300;
            if (!ok) {
                log.warn("短信网关返回 {}：{}", res.statusCode(), res.body());
            }
            return ok;
        } catch (Exception e) {
            log.warn("短信发送失败 mobile={}: {}", mobile, e.getMessage());
            return false;
        }
    }
}

package com.qingmeili.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 放宽日期时间的入参格式。
 *
 * 后台 Element Plus 的日期控件给出「2026-09-01」，C 端 picker 给出「2026-09-01 00:00:00」，
 * 而 Jackson 默认只认 ISO 的「2026-09-01T00:00:00」——于是新增/编辑任何带日期字段的记录
 * （轮播图投放时间、活动起止时间等）都会以 400「请求体格式错误」失败。
 * 这里同时接受三种写法，纯日期按当天零点补齐。出参仍是 ISO，前端已按 ISO 解析。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter SPACE_SEPARATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer lenientDateTimeCustomizer() {
        // modulesToInstall 在内置 JavaTimeModule 之后注册，确保覆盖默认的 LocalDateTime 反序列化器
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());
            module.addDeserializer(LocalDate.class, new LenientLocalDateDeserializer());
            builder.modulesToInstall(module);
        };
    }

    static class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String raw = p.getText();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String text = raw.trim();
            try {
                if (text.length() <= 10) {
                    return LocalDate.parse(text).atStartOfDay();
                }
                return text.contains("T") ? LocalDateTime.parse(text) : LocalDateTime.parse(text, SPACE_SEPARATED);
            } catch (DateTimeParseException e) {
                throw new IOException("时间格式不正确，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss：" + text);
            }
        }
    }

    static class LenientLocalDateDeserializer extends JsonDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String raw = p.getText();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String text = raw.trim();
            try {
                return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
            } catch (DateTimeParseException e) {
                throw new IOException("日期格式不正确，应为 yyyy-MM-dd：" + text);
            }
        }
    }
}

package com.qingmeili.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 编辑型内容从 classpath JSON 加载并缓存：首页文案与入口配置、导览图底图数据。
 * 景点、路线、商品、订单等业务数据一律走数据库，后台改完 C 端即时生效。
 */
@Slf4j
@Component
public class ContentStore {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    private JsonNode homeConfig;
    @Getter
    private JsonNode guide;

    @PostConstruct
    public void load() {
        homeConfig = read("content/home-config.json");
        guide = read("content/guide.json");
    }

    private JsonNode read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            log.warn("内容资源加载失败 {}: {}", path, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }
}

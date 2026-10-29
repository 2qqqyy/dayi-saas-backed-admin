package com.qingmeili.controller;

import com.qingmeili.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根路径探活与引导。
 * 直接访问后端 8080 时不再返回 404 JSON，而是给出服务信息与入口指引。
 * 注意：JwtInterceptor 只拦截 /api/**，此接口天然公开。
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Result<Map<String, Object>> index() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "qingmeili-backend");
        info.put("version", "1.0.0");
        info.put("status", "running");
        info.put("apiPrefix", "/api");
        info.put("examples", new String[]{"/api/home/config", "/api/product", "/api/banners"});
        info.put("hint", "H5 商城请访问前端 5173 端口，管理后台请访问 5175 端口");
        return Result.ok(info);
    }
}

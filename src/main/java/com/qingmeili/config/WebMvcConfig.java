package com.qingmeili.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * WebMvc 配置：注册 JWT 拦截器，配置拦截/放行路径
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Value("${qingmeili.upload-dir:uploads}")
    private String uploadDir;

    /** 商品/路线等配图源目录。小程序主包限 2M，配图不能打进小程序包，改由后端提供 */
    @Value("${qingmeili.static-dir:static}")
    private String staticDir;

    /** 本地上传文件静态访问 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = new File(uploadDir).getAbsolutePath().replace("\\", "/") + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + location);

        String imgLocation = new File(staticDir, "images").getAbsolutePath().replace("\\", "/") + "/";
        registry.addResourceHandler("/static/images/**").addResourceLocations("file:" + imgLocation);

        String audioLocation = new File(staticDir, "audio").getAbsolutePath().replace("\\", "/") + "/";
        registry.addResourceHandler("/static/audio/**").addResourceLocations("file:" + audioLocation);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 管理员登录
                        "/api/admin/auth/login",
                        "/api/admin/auth/captcha",
                        // 认证相关
                        "/api/auth/**",
                        // 景点公开接口
                        "/api/scenic",
                        "/api/scenic/**",
                        // 攻略文章公开接口
                        "/api/guides",
                        "/api/guides/**",
                        // 采摘果园公开接口
                        "/api/picking",
                        "/api/picking/{id}",
                        "/api/picking/orchards",
                        "/api/picking/orchards/**",
                        // 民宿公开接口
                        "/api/homestay",
                        "/api/homestay/**",
                        // 商品公开接口
                        "/api/product",
                        "/api/product/categories",
                        "/api/product/list",
                        "/api/product/{id}",
                        "/api/product/*/reviews",
                        // 首页内容配置
                        "/api/home/**",
                        // 旅行内容（景点/路线/导览图/地图点位）
                        "/api/travel/**",
                        // C端短信与登录别名
                        "/api/user/sms",
                        "/api/user/login",
                        // 商户端登录（放行，否则无法获取 merchantToken）
                        "/api/merchant/login",
                        "/api/merchant/logout",
                        "/api/merchant/auth/sms/code",
                        // 商家端自助入驻申请（注册页在登录前提交）
                        "/api/merchant/apply/public",
                        // AI 向导
                        "/api/ai/**",
                        // 公共数据接口（只读）；上传不在白名单内，必须登录
                        "/api/common/appearance",
                        "/api/common/notices",
                        "/api/common/notices/*",
                        "/api/common/configs",
                        "/api/common/placeholder",
                        "/api/common/region",
                        "/api/common/dicts",
                        "/api/common/wx/config",
                        // 入驻申请（登录前）提交资质图的专用上传口
                        "/api/common/upload-public",
                        // 地图标记点
                        "/api/map/markers",
                        // 轮播图
                        "/api/banners",
                        // 静态资源
                        "/uploads/**",
                        "/error"
                );
    }
}

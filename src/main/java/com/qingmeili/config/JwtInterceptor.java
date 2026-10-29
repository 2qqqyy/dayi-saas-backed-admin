package com.qingmeili.config;

import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * JWT 拦截器：解析 token、注入 UserContext、校验 @RequireRole
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 非 Controller 方法直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            sendError(response, 401, "未登录或 token 缺失");
            return false;
        }

        try {
            if (jwtUtil.isTokenExpired(token)) {
                sendError(response, 401, "token 已过期，请重新登录");
                return false;
            }

            Claims claims = jwtUtil.parseToken(token);
            UserContext ctx = new UserContext();

            // 解析 userId（兼容 adminId）
            Object userId = claims.get("userId");
            if (userId != null) {
                ctx.setUserId(Long.valueOf(userId.toString()));
            } else {
                Object adminId = claims.get("adminId");
                if (adminId != null) {
                    ctx.setUserId(Long.valueOf(adminId.toString()));
                }
            }

            ctx.setUserType(claims.get("userType", String.class));
            ctx.setRole(claims.get("role", String.class));

            Object merchantId = claims.get("merchantId");
            if (merchantId != null) {
                ctx.setMerchantId(Long.valueOf(merchantId.toString()));
            }

            UserContext.set(ctx);

            // 校验 @RequireRole 注解
            RequireRole methodAnnotation = handlerMethod.getMethodAnnotation(RequireRole.class);
            RequireRole classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            RequireRole annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;

            if (annotation != null) {
                String currentRole = ctx.getRole();
                boolean allowed = Arrays.asList(annotation.value()).contains(currentRole);
                if (!allowed) {
                    sendError(response, 403, "权限不足，当前角色：" + currentRole);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("token 解析失败: {}", e.getMessage());
            sendError(response, 401, "token 无效");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }


    /**
     * 从请求头提取 Bearer token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // 兼容 query 参数
        return request.getParameter("token");
    }

    private void sendError(HttpServletResponse response, int status, String msg) throws Exception {
        // HTTP 状态码与业务码保持一致（401/403），body 内仍保留 code 字段兼容旧前端
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}

package com.qingmeili.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token（游客端）
     */
    public String generateUserToken(Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userType", "user");
        claims.put("role", role);
        return createToken(claims, "user-" + userId);
    }

    /**
     * 生成 token（管理员端）
     */
    public String generateAdminToken(Long adminId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("adminId", adminId);
        claims.put("userType", "admin");
        claims.put("role", role);
        return createToken(claims, "admin-" + adminId);
    }

    /**
     * 生成 token（商户端）
     */
    public String generateMerchantToken(Long userId, Long merchantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("merchantId", merchantId);
        claims.put("userType", "merchant");
        claims.put("role", "merchant");
        return createToken(claims, "merchant-" + userId);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 token
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 token 获取用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId != null) {
            return Long.valueOf(userId.toString());
        }
        return claims.get("adminId", Long.class);
    }

    /**
     * 从 token 获取用户类型
     */
    public String getUserType(String token) {
        return parseToken(token).get("userType", String.class);
    }

    /**
     * 从 token 获取角色
     */
    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * 从 token 获取商户ID
     */
    public Long getMerchantId(String token) {
        Claims claims = parseToken(token);
        Object merchantId = claims.get("merchantId");
        return merchantId != null ? Long.valueOf(merchantId.toString()) : null;
    }

    /**
     * 校验 token 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}

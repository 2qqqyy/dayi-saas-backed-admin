package com.qingmeili.common.context;

import lombok.Data;

/**
 * 当前登录用户上下文
 */
@Data
public class UserContext {
    private Long userId;
    private String userType;  // user / admin / merchant
    private String role;
    private Long merchantId;  // 商户端专用

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    // 注意：静态方法与 Lombok 生成的实例 getter 同名时，Lombok 会跳过实例 getter 的生成，
    // 因此静态方法体内必须直接访问字段，调用 getXxx() 会形成无限递归（StackOverflowError）。
    public static Long getUserId() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.userId : null;
    }

    public static String getUserType() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.userType : null;
    }

    public static String getRole() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.role : null;
    }

    public static Long getMerchantId() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.merchantId : null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}

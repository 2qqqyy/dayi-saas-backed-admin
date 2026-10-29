package com.qingmeili.common.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解
 * 标注在 Controller 方法上，指定允许访问的角色
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /**
     * 允许的角色列表，满足其一即可
     * 可选值：guest, tourist, merchant, admin, super_admin
     */
    String[] value();
}

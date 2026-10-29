package com.qingmeili.config;

import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.entity.Admin;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.OperationLogEntity;
import com.qingmeili.mapper.AdminMapper;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志 AOP 切面，拦截 @OperationLog 注解自动记录日志
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final AdminMapper adminMapper;
    private final MerchantMapper merchantMapper;

    /**
     * 成功与失败都要留痕：失败日志的 result=FAIL，异常原样抛出，不改变接口行为。
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        try {
            Object result = point.proceed();
            write(operationLog, point, "SUCCESS", null);
            return result;
        } catch (Throwable t) {
            write(operationLog, point, "FAIL", t.getMessage());
            throw t;
        }
    }

    private void write(OperationLog operationLog, ProceedingJoinPoint point, String result, String errorMsg) {
        try {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setAdminId(UserContext.getUserId());
            entity.setAdminName(operatorName());
            entity.setModule(operationLog.module());
            entity.setAction(operationLog.action());
            entity.setResult(result);

            // 获取请求信息
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entity.setIp(getClientIp(request));
                entity.setUserAgent(request.getHeader("User-Agent"));
            }

            String detail = point.getSignature().toShortString()
                    + (attrs != null ? " " + attrs.getRequest().getMethod() + " " + attrs.getRequest().getRequestURI() : "");
            if (errorMsg != null && !errorMsg.isBlank()) {
                detail = detail + " | 失败原因：" + errorMsg;
            }
            entity.setDetail(detail.length() > 1000 ? detail.substring(0, 1000) : detail);
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

    /** 操作人展示名：后台取 admin.real_name/username，商户端取商户名，查不到留空由前端兜底 */
    private String operatorName() {
        String userType = UserContext.getUserType();
        if ("admin".equals(userType)) {
            Long adminId = UserContext.getUserId();
            if (adminId == null) return null;
            Admin admin = adminMapper.selectById(adminId);
            if (admin == null) return null;
            return admin.getRealName() != null && !admin.getRealName().isBlank()
                    ? admin.getRealName() : admin.getUsername();
        }
        if ("merchant".equals(userType)) {
            Long merchantId = UserContext.getMerchantId();
            if (merchantId == null) return null;
            Merchant merchant = merchantMapper.selectById(merchantId);
            return merchant == null ? null : merchant.getName();
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}

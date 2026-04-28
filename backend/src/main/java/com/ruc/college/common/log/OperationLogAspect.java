package com.ruc.college.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruc.college.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        Object result = point.proceed();

        try {
            Long userId = UserContext.getUserId();
            String ip = getClientIp();
            String detail = objectMapper.writeValueAsString(point.getArgs());

            jdbcTemplate.update(
                    "INSERT INTO sys_operation_log (user_id, module, action, detail, ip) VALUES (?, ?, ?, ?, ?)",
                    userId, operationLog.module(), operationLog.action(), detail, ip
            );
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }

        return result;
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}

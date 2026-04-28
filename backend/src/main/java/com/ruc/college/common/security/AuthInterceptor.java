package com.ruc.college.common.security;

import com.ruc.college.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 解析 Token
        String token = extractToken(request);
        if (token == null) {
            throw new BusinessException(401, "未登录或Token已过期");
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(Long.valueOf(claims.getSubject()));
            loginUser.setStudentId(claims.get("studentId", String.class));
            loginUser.setRoleLevel(claims.get("roleLevel", Integer.class));
            UserContext.set(loginUser);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(401, "Token已过期，请重新登录");
        } catch (JwtException e) {
            throw new BusinessException(401, "无效的Token");
        }

        // 检查角色权限
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            int currentLevel = UserContext.getRoleLevel();
            if (currentLevel > requireRole.minLevel()) {
                throw new BusinessException(403, "权限不足，需要" + requireRole.minLevel() + "级及以上权限");
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.remove();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}

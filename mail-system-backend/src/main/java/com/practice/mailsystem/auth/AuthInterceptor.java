package com.practice.mailsystem.auth;

import com.practice.mailsystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> WHITE_LIST = Set.of(
            "/login/loginbyemail",
            "/login/logout",
            "/api/auth/login",
            "/api/auth/register"
    );

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || WHITE_LIST.contains(path)) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        try {
            UserContext.set(jwtService.parse(token));
        } catch (Exception ex) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 仅从 Header 读取 Token，避免 query 参数进入日志/Referer */
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("X-Token");
        if (StringUtils.hasText(token)) {
            return token.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }
}

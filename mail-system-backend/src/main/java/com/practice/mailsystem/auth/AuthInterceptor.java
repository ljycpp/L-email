package com.practice.mailsystem.auth;

import com.practice.mailsystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
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

        String token = request.getHeader("X-Token");
        if (token == null || token.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }
        if (token == null || token.isBlank()) {
            if ("/user/info".equals(path) || isAttachmentDownload(path)) {
                token = request.getParameter("token");
            }
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        UserContext.set(jwtService.parse(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean isAttachmentDownload(String path) {
        return path != null && path.startsWith("/api/attachments/") && path.endsWith("/download");
    }
}

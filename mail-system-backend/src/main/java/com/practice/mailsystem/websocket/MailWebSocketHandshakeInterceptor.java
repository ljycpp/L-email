package com.practice.mailsystem.websocket;

import com.practice.mailsystem.auth.JwtService;
import com.practice.mailsystem.auth.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class MailWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public MailWebSocketHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest raw = servletRequest.getServletRequest();
        String token = raw.getParameter("token");
        if (token == null || token.isBlank()) {
            List<String> tokenHeaders = request.getHeaders().get("X-Token");
            if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
                token = tokenHeaders.get(0);
            }
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            LoginUser loginUser = jwtService.parse(token);
            attributes.put("userId", loginUser.userId());
            attributes.put("email", loginUser.email());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}

package com.practice.mailsystem.websocket;

import com.practice.mailsystem.auth.JwtService;
import com.practice.mailsystem.auth.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class MailWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final WsTicketService wsTicketService;
    private final JwtService jwtService;

    public MailWebSocketHandshakeInterceptor(WsTicketService wsTicketService, JwtService jwtService) {
        this.wsTicketService = wsTicketService;
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        LoginUser loginUser = resolveUser(request);
        if (loginUser == null) {
            return false;
        }
        attributes.put("userId", loginUser.userId());
        attributes.put("email", loginUser.email());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    /**
     * 浏览器：先调 /api/ws/ticket 获取一次性 ticket，再通过 ?ticket= 握手。
     * 非浏览器客户端：可使用 X-Token / Authorization: Bearer。
     */
    private LoginUser resolveUser(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest raw = servletRequest.getServletRequest();
            String ticket = raw.getParameter("ticket");
            if (StringUtils.hasText(ticket)) {
                return wsTicketService.consume(ticket);
            }
        }
        String headerToken = firstHeader(request, "X-Token");
        if (!StringUtils.hasText(headerToken)) {
            String authorization = firstHeader(request, "Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                headerToken = authorization.substring(7).trim();
            }
        }
        if (!StringUtils.hasText(headerToken)) {
            return null;
        }
        try {
            return jwtService.parse(headerToken);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstHeader(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}

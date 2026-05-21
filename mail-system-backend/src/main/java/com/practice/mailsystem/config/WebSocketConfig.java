package com.practice.mailsystem.config;

import com.practice.mailsystem.websocket.MailWebSocketHandshakeInterceptor;
import com.practice.mailsystem.websocket.MailWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MailWebSocketHandler mailWebSocketHandler;
    private final MailWebSocketHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(MailWebSocketHandler mailWebSocketHandler,
                           MailWebSocketHandshakeInterceptor handshakeInterceptor) {
        this.mailWebSocketHandler = mailWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mailWebSocketHandler, "/ws/mail")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}

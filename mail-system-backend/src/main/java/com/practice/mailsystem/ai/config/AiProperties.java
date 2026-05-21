package com.practice.mailsystem.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String vveaiChatUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int maxContentLength,
        String secretKey
) {
}

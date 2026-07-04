package com.practice.mailsystem.config;

import com.practice.mailsystem.ai.config.AiProperties;
import com.practice.mailsystem.auth.JwtProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

@Component
public class SecurityStartupValidator {

    private static final Set<String> KNOWN_WEAK_SECRETS = Set.of(
            "l-email-dev-jwt-secret-2026-safe-minimum-32",
            "l-email-ai-secret-key-2026-32!!!"
    );

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final AiProperties aiProperties;

    public SecurityStartupValidator(Environment environment,
                                    JwtProperties jwtProperties,
                                    AiProperties aiProperties) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.aiProperties = aiProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateSecrets() {
        boolean devProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile));
        validateSecret("APP_JWT_SECRET", jwtProperties.getSecret(), 32, devProfile);
        validateSecret("APP_AI_SECRET_KEY", aiProperties.secretKey(), 32, devProfile);
    }

    private void validateSecret(String envName, String value, int minLength, boolean devProfile) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envName + " 未配置，请通过环境变量注入");
        }
        if (value.length() < minLength) {
            throw new IllegalStateException(envName + " 长度至少 " + minLength + " 字符");
        }
        if (!devProfile && KNOWN_WEAK_SECRETS.contains(value)) {
            throw new IllegalStateException(envName + " 不能使用内置默认值，生产环境必须设置独立密钥");
        }
    }
}

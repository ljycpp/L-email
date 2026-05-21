package com.practice.mailsystem.ai.vo;

public record AiConfigVO(
        String providerName,
        String apiKeyMasked,
        String modelName,
        Boolean enabled,
        Boolean configured,
        String lastTestStatus,
        String lastTestMessage
) {
}

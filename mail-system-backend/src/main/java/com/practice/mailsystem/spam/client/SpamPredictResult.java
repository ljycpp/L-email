package com.practice.mailsystem.spam.client;

import java.util.List;

public record SpamPredictResult(
        String label,
        double spamScore,
        double hamScore,
        double confidence,
        List<String> reasonTokens
) {
    public boolean isSpam() {
        return "spam".equalsIgnoreCase(label);
    }
}

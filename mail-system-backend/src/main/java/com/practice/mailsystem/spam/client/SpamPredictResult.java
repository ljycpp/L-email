package com.practice.mailsystem.spam.client;

public record SpamPredictResult(
        String label,
        double spamScore,
        double hamScore,
        double confidence
) {
    public boolean isSpam() {
        return "spam".equalsIgnoreCase(label);
    }
}

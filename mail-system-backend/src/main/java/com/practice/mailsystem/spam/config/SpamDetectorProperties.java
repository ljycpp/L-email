package com.practice.mailsystem.spam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spam-detector")
public class SpamDetectorProperties {

    private boolean enabled = true;
    private String modelPath = "classpath:spam_model.json";
    private double spamThreshold = 0.5;
    private int topReasonCount = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public double getSpamThreshold() {
        return spamThreshold;
    }

    public void setSpamThreshold(double spamThreshold) {
        this.spamThreshold = spamThreshold;
    }

    public int getTopReasonCount() {
        return topReasonCount;
    }

    public void setTopReasonCount(int topReasonCount) {
        this.topReasonCount = topReasonCount;
    }
}

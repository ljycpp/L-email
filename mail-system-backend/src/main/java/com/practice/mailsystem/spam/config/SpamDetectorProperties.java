package com.practice.mailsystem.spam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spam-detector")
public class SpamDetectorProperties {

    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8000";
    private String username = "admin";
    private String password = "admin123";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
    private double spamThreshold = 0.5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public double getSpamThreshold() {
        return spamThreshold;
    }

    public void setSpamThreshold(double spamThreshold) {
        this.spamThreshold = spamThreshold;
    }
}

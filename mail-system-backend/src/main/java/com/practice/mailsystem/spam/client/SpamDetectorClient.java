package com.practice.mailsystem.spam.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.mailsystem.spam.config.SpamDetectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SpamDetectorClient {

    private static final Logger log = LoggerFactory.getLogger(SpamDetectorClient.class);
    private static final Pattern SESSION_PATTERN = Pattern.compile("mail_radar_session=([^;]+)");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SpamDetectorProperties properties;
    private volatile String sessionCookie;

    public SpamDetectorClient(SpamDetectorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<SpamPredictResult> predict(String subject, String body) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.of(executeWithSession(() -> doPredict(subject, body)));
        } catch (Exception ex) {
            log.warn("垃圾邮件检测服务不可用: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void testConnection() {
        if (!properties.isEnabled()) {
            throw new RestClientException("垃圾邮件检测已关闭");
        }
        invalidateSession();
        executeWithSession(() -> {
            doPredict("Connection test", "Connection test");
            return null;
        });
    }

    private <T> T executeWithSession(SpamAction<T> action) {
        ensureSession();
        try {
            return action.run();
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                log.info("垃圾邮件检测会话失效，重新登录后重试。");
                invalidateSession();
                ensureSession();
                return action.run();
            }
            throw ex;
        }
    }

    private SpamPredictResult doPredict(String subject, String body) {
        String url = apiUrl("/api/predict-text");
        String jsonBody = toJson(Map.of(
                "subject", subject == null ? "" : subject,
                "body", body == null ? "" : body
        ));
        ResponseEntity<Map<String, Object>> response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (StringUtils.hasText(sessionCookie)) {
                        headers.add(HttpHeaders.COOKIE, sessionCookie);
                    }
                })
                .body(jsonBody)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        Map<String, Object> data = response.getBody();
        if (data == null) {
            throw new RestClientException("检测服务返回了空响应");
        }
        return parseResult(data);
    }

    private SpamPredictResult parseResult(Map<String, Object> data) {
        Object labelValue = data.containsKey("label") ? data.get("label") : "ham";
        String label = String.valueOf(labelValue);
        Map<String, Object> scores = data.get("scores") instanceof Map<?, ?> map
                ? castMap(map)
                : Map.of();
        double spam = toDouble(scores.get("spam"));
        double ham = toDouble(scores.get("ham"));
        double confidence = toDouble(data.get("confidence"));
        if (confidence <= 0) {
            confidence = Math.max(spam, ham);
        }
        return new SpamPredictResult(label, spam, ham, confidence);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private void ensureSession() {
        if (StringUtils.hasText(sessionCookie)) {
            return;
        }
        synchronized (this) {
            if (StringUtils.hasText(sessionCookie)) {
                return;
            }
            login();
        }
    }

    private void invalidateSession() {
        synchronized (this) {
            sessionCookie = null;
        }
    }

    private void login() {
        String url = apiUrl("/api/login");
        String username = resolveCredential(properties.getUsername(), "admin");
        String password = resolveCredential(properties.getPassword(), "admin123");
        String jsonBody = toJson(Map.of("username", username, "password", password));
        log.debug("垃圾邮件检测登录请求 url={} user={}", url, username);
        try {
            ResponseEntity<Map<String, Object>> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<>() {
                    });
            sessionCookie = extractSessionCookie(response.getHeaders());
            if (!StringUtils.hasText(sessionCookie)) {
                throw new RestClientException("无法获取垃圾邮件检测服务登录 Cookie");
            }
            log.info("垃圾邮件检测服务登录成功，用户={}", username);
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            log.warn("垃圾邮件检测登录失败 status={} body={}", ex.getStatusCode().value(), body);
            throw new RestClientException("登录检测服务失败(" + ex.getStatusCode().value() + "): " + body, ex);
        }
    }

    private String extractSessionCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        for (String cookie : cookies) {
            Matcher matcher = SESSION_PATTERN.matcher(cookie);
            if (matcher.find()) {
                return "mail_radar_session=" + matcher.group(1);
            }
        }
        return null;
    }

    private String apiUrl(String path) {
        return properties.getBaseUrl().replaceAll("/$", "") + path;
    }

    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new RestClientException("无法序列化检测服务请求体", ex);
        }
    }

    private String resolveCredential(String configured, String fallback) {
        if (!StringUtils.hasText(configured)) {
            log.warn("spam-detector 配置为空，使用内置默认账号");
            return fallback;
        }
        return configured.trim();
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0D;
        }
    }

    @FunctionalInterface
    private interface SpamAction<T> {
        T run();
    }
}

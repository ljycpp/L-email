package com.practice.mailsystem.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PublicUrlBuilder {

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public String attachmentDownloadUrl(Long attachmentId) {
        String baseUrl = publicBaseUrl == null ? "http://localhost:8080" : publicBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/api/attachments/" + attachmentId + "/download";
    }
}

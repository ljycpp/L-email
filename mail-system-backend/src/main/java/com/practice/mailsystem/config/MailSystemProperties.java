package com.practice.mailsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * L 邮箱：系统级配置（域名与 {@code application.yml} 中 mail-system.email-domain 一致）。
 */
@ConfigurationProperties(prefix = "mail-system")
public record MailSystemProperties(String emailDomain) {
}

package com.practice.mailsystem;

import com.practice.mailsystem.ai.config.AiProperties;
import com.practice.mailsystem.auth.JwtProperties;
import com.practice.mailsystem.config.MailSystemProperties;
import com.practice.mailsystem.spam.config.SpamDetectorProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan({
        "com.practice.mailsystem.ai.mapper",
        "com.practice.mailsystem.user.mapper",
        "com.practice.mailsystem.mail.mapper",
        "com.practice.mailsystem.attachment.mapper",
        "com.practice.mailsystem.directory.mapper"
})
@EnableConfigurationProperties({
        JwtProperties.class,
        MailSystemProperties.class,
        AiProperties.class,
        SpamDetectorProperties.class
})
public class MailSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailSystemApplication.class, args);
    }
}

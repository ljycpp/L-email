package com.practice.mailsystem.spam.service;

import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.mail.entity.MailMessage;
import com.practice.mailsystem.mail.entity.MailUserBox;
import com.practice.mailsystem.mail.mapper.MailUserBoxMapper;
import com.practice.mailsystem.spam.client.SpamPredictResult;
import com.practice.mailsystem.spam.config.SpamDetectorProperties;
import com.practice.mailsystem.spam.dto.SpamAutoFilterStatusVO;
import com.practice.mailsystem.spam.plugin.SpamDetectionPlugin;
import com.practice.mailsystem.user.entity.SysUser;
import com.practice.mailsystem.user.mapper.SysUserMapper;
import com.practice.mailsystem.websocket.MailNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class SpamAutoFilterService {

    private static final String BOX_INBOX = "INBOX";

    private final SysUserMapper userMapper;
    private final MailUserBoxMapper mailUserBoxMapper;
    private final SpamDetectionPlugin spamDetectionPlugin;
    private final SpamDetectorProperties properties;
    private final MailNotificationService mailNotificationService;

    public SpamAutoFilterService(SysUserMapper userMapper,
                                 MailUserBoxMapper mailUserBoxMapper,
                                 SpamDetectionPlugin spamDetectionPlugin,
                                 SpamDetectorProperties properties,
                                 MailNotificationService mailNotificationService) {
        this.userMapper = userMapper;
        this.mailUserBoxMapper = mailUserBoxMapper;
        this.spamDetectionPlugin = spamDetectionPlugin;
        this.properties = properties;
        this.mailNotificationService = mailNotificationService;
    }

    public SpamAutoFilterStatusVO getStatus() {
        Long userId = UserContext.requireUserId();
        return new SpamAutoFilterStatusVO(isAutoFilterEnabled(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable() {
        Long userId = UserContext.requireUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "?????");
        }
        try {
            spamDetectionPlugin.testConnection();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            String detail = ex.getMessage() == null ? "" : ex.getMessage();
            throw new BusinessException(
                    502,
                    "???????????????????????????????"
                            + (detail.isBlank() ? "" : " ??: " + detail)
            );
        }
        user.setAutoSpamFilter(1);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable() {
        Long userId = UserContext.requireUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "?????");
        }
        user.setAutoSpamFilter(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public boolean tryAutoFilter(Long ownerUserId,
                                 MailUserBox inboxBox,
                                 MailMessage message,
                                 String senderName,
                                 String senderMail) {
        if (!properties.isEnabled() || inboxBox == null || message == null) {
            return false;
        }
        if (!isAutoFilterEnabled(ownerUserId)) {
            return false;
        }
        if (!BOX_INBOX.equals(inboxBox.getBoxType()) || (inboxBox.getSpamFlag() != null && inboxBox.getSpamFlag() == 1)) {
            return false;
        }

        String subject = StringUtils.hasText(message.getSubject()) ? message.getSubject() : "";
        String body = resolveBodyText(message);
        Optional<SpamPredictResult> optional = spamDetectionPlugin.predict(subject, body);
        if (optional.isEmpty()) {
            return false;
        }

        SpamPredictResult result = optional.get();
        boolean isSpam = result.isSpam() && result.spamScore() >= properties.getSpamThreshold();
        if (!isSpam) {
            return false;
        }

        String reason = buildReason(result, subject);
        LocalDateTime now = LocalDateTime.now();
        inboxBox.setSpamFlag(1);
        inboxBox.setSpamReason(reason);
        inboxBox.setSpamScore(result.spamScore());
        inboxBox.setSpamDetectedAt(now);
        inboxBox.setUpdatedAt(now);
        mailUserBoxMapper.updateById(inboxBox);

        String title = StringUtils.hasText(message.getSubject()) ? message.getSubject() : "?????";
        mailNotificationService.notifySpamFiltered(
                ownerUserId,
                message.getId(),
                title,
                senderName,
                senderMail,
                reason
        );
        return true;
    }

    public boolean isAutoFilterEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = userMapper.selectById(userId);
        return user != null && user.getAutoSpamFilter() != null && user.getAutoSpamFilter() == 1;
    }

    public String buildReason(SpamPredictResult result, String subject) {
        String safeSubject = subject == null ? "" : subject;
        if (safeSubject.length() > 50) {
            safeSubject = safeSubject.substring(0, 50) + "...";
        }
        String extraReasons = result.reasonTokens() == null || result.reasonTokens().isEmpty()
                ? ""
                : "??????" + String.join("?", result.reasonTokens());
        return String.format(
                Locale.CHINA,
                "????????????? %.1f%%?spam ?? %.1f%%?ham ?? %.1f%%?????%s%s",
                result.confidence() * 100,
                result.spamScore() * 100,
                result.hamScore() * 100,
                safeSubject.isEmpty() ? "?????" : safeSubject,
                extraReasons
        );
    }

    private String resolveBodyText(MailMessage message) {
        if (StringUtils.hasText(message.getContentText())) {
            return message.getContentText();
        }
        if (StringUtils.hasText(message.getContentHtml())) {
            return message.getContentHtml().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
        return "";
    }
}

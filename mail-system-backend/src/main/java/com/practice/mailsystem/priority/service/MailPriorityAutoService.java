package com.practice.mailsystem.priority.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.mailsystem.ai.config.AiProperties;
import com.practice.mailsystem.ai.entity.UserAiConfig;
import com.practice.mailsystem.ai.mapper.UserAiConfigMapper;
import com.practice.mailsystem.ai.provider.AiProvider;
import com.practice.mailsystem.ai.util.AiApiKeyCipher;
import com.practice.mailsystem.ai.vo.MailPriorityResult;
import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.mail.entity.MailMessage;
import com.practice.mailsystem.mail.entity.MailUserBox;
import com.practice.mailsystem.mail.mapper.MailUserBoxMapper;
import com.practice.mailsystem.priority.dto.PriorityAutoFilterStatusVO;
import com.practice.mailsystem.user.entity.SysUser;
import com.practice.mailsystem.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class MailPriorityAutoService {

    private static final Logger log = LoggerFactory.getLogger(MailPriorityAutoService.class);
    private static final String BOX_INBOX = "INBOX";

    private final SysUserMapper userMapper;
    private final MailUserBoxMapper mailUserBoxMapper;
    private final UserAiConfigMapper userAiConfigMapper;
    private final AiApiKeyCipher aiApiKeyCipher;
    private final AiProvider aiProvider;
    private final AiProperties aiProperties;

    public MailPriorityAutoService(SysUserMapper userMapper,
                                   MailUserBoxMapper mailUserBoxMapper,
                                   UserAiConfigMapper userAiConfigMapper,
                                   AiApiKeyCipher aiApiKeyCipher,
                                   AiProvider aiProvider,
                                   AiProperties aiProperties) {
        this.userMapper = userMapper;
        this.mailUserBoxMapper = mailUserBoxMapper;
        this.userAiConfigMapper = userAiConfigMapper;
        this.aiApiKeyCipher = aiApiKeyCipher;
        this.aiProvider = aiProvider;
        this.aiProperties = aiProperties;
    }

    public PriorityAutoFilterStatusVO getStatus() {
        Long userId = requiredUserId();
        return new PriorityAutoFilterStatusVO(isAutoPriorityEnabled(userId), isAiConfigured(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable() {
        Long userId = requiredUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!isAiConfigured(userId)) {
            throw new BusinessException(400, "请先在 AI 设置中配置并启用接入密钥");
        }
        UserAiConfig config = findEnabledConfig(userId)
                .orElseThrow(() -> new BusinessException(400, "请先在 AI 设置中配置并启用接入密钥"));
        aiProvider.testConnection(aiApiKeyCipher.decrypt(config.getApiKeyEncrypted()), config.getModelName());
        user.setAutoPriorityFilter(1);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable() {
        Long userId = requiredUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setAutoPriorityFilter(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void tryAutoPriority(Long ownerUserId,
                                MailUserBox inboxBox,
                                MailMessage message,
                                String senderName,
                                String senderMail) {
        if (inboxBox == null || message == null || ownerUserId == null) {
            return;
        }
        if (!isAutoPriorityEnabled(ownerUserId)) {
            return;
        }
        if (!BOX_INBOX.equals(inboxBox.getBoxType())) {
            return;
        }
        if (inboxBox.getSpamFlag() != null && inboxBox.getSpamFlag() == 1) {
            return;
        }

        Optional<UserAiConfig> configOptional = findEnabledConfig(ownerUserId);
        if (configOptional.isEmpty()) {
            return;
        }
        UserAiConfig config = configOptional.get();
        String apiKey = aiApiKeyCipher.decrypt(config.getApiKeyEncrypted());
        String modelName = config.getModelName();

        String subject = StringUtils.hasText(message.getSubject()) ? message.getSubject() : "（无主题）";
        String body = truncate(resolveBodyText(message));

        try {
            MailPriorityResult result = aiProvider.classifyPriority(
                    senderName,
                    senderMail,
                    subject,
                    body,
                    apiKey,
                    modelName
            );
            applyPriority(inboxBox, result);
        } catch (BusinessException ex) {
            log.warn("Auto priority skipped for user {} mail {}: {}", ownerUserId, message.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Auto priority failed for user {} mail {}", ownerUserId, message.getId(), ex);
        }
    }

    public boolean isAutoPriorityEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = userMapper.selectById(userId);
        return user != null && user.getAutoPriorityFilter() != null && user.getAutoPriorityFilter() == 1;
    }

    public boolean isAiConfigured(Long userId) {
        return findEnabledConfig(userId).isPresent();
    }

    private void applyPriority(MailUserBox inboxBox, MailPriorityResult result) {
        if (result == null) {
            return;
        }
        String level = normalizeLevel(result.level());
        double score = clampScore(result.score());
        String reason = StringUtils.hasText(result.reason()) ? result.reason().trim() : "系统已评估邮件优先级";
        if (reason.length() > 500) {
            reason = reason.substring(0, 500);
        }

        LocalDateTime now = LocalDateTime.now();
        inboxBox.setPriorityLevel(level);
        inboxBox.setPriorityScore(score);
        inboxBox.setPriorityReason(reason);
        inboxBox.setPriorityScoredAt(now);
        inboxBox.setImportantFlag("HIGH".equals(level) ? 1 : 0);
        inboxBox.setUpdatedAt(now);
        mailUserBoxMapper.updateById(inboxBox);
    }

    private String normalizeLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return "MEDIUM";
        }
        String upper = level.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "HIGH", "MEDIUM", "LOW" -> upper;
            default -> "MEDIUM";
        };
    }

    private double clampScore(double score) {
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return 0.5;
        }
        return Math.max(0, Math.min(1, score));
    }

    private Optional<UserAiConfig> findEnabledConfig(Long userId) {
        UserAiConfig config = userAiConfigMapper.selectOne(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId)
                .last("limit 1"));
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(config.getApiKeyEncrypted()) || !StringUtils.hasText(config.getModelName())) {
            return Optional.empty();
        }
        return Optional.of(config);
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

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int maxLength = Math.max(aiProperties.maxContentLength(), 500);
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private Long requiredUserId() {
        Long userId = UserContext.requireUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }
}

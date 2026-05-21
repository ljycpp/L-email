package com.practice.mailsystem.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.mailsystem.ai.dto.AiConfigSaveRequest;
import com.practice.mailsystem.ai.dto.AiConfigTestRequest;
import com.practice.mailsystem.ai.entity.UserAiConfig;
import com.practice.mailsystem.ai.enums.AiRecordStatus;
import com.practice.mailsystem.ai.mapper.UserAiConfigMapper;
import com.practice.mailsystem.ai.provider.AiProvider;
import com.practice.mailsystem.ai.service.AiConfigService;
import com.practice.mailsystem.ai.util.AiApiKeyCipher;
import com.practice.mailsystem.ai.util.AiKeyMaskUtil;
import com.practice.mailsystem.ai.vo.AiConfigTestVO;
import com.practice.mailsystem.ai.vo.AiConfigVO;
import com.practice.mailsystem.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AiConfigServiceImpl implements AiConfigService {

    private static final String PROVIDER_NAME = "VVEAI";

    private final UserAiConfigMapper userAiConfigMapper;
    private final AiApiKeyCipher aiApiKeyCipher;
    private final AiProvider aiProvider;

    public AiConfigServiceImpl(UserAiConfigMapper userAiConfigMapper,
                               AiApiKeyCipher aiApiKeyCipher,
                               AiProvider aiProvider) {
        this.userAiConfigMapper = userAiConfigMapper;
        this.aiApiKeyCipher = aiApiKeyCipher;
        this.aiProvider = aiProvider;
    }

    @Override
    public AiConfigVO getCurrentUserConfig(Long userId) {
        UserAiConfig config = findByUserId(userId);
        if (config == null) {
            return new AiConfigVO(PROVIDER_NAME, "", "", false, false, null, null);
        }
        String apiKey = aiApiKeyCipher.decrypt(config.getApiKeyEncrypted());
        return new AiConfigVO(
                PROVIDER_NAME,
                AiKeyMaskUtil.mask(apiKey),
                config.getModelName(),
                config.getEnabled() != null && config.getEnabled() == 1,
                true,
                config.getLastTestStatus(),
                config.getLastTestMessage()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateConfig(Long userId, AiConfigSaveRequest request) {
        UserAiConfig existing = findByUserId(userId);
        String apiKey = resolveApiKey(request.apiKey(), existing);

        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("请填写接入密钥");
        }

        UserAiConfig entity = existing == null ? new UserAiConfig() : existing;
        if (entity.getId() == null) {
            entity.setUserId(userId);
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setApiKeyEncrypted(aiApiKeyCipher.encrypt(apiKey));
        entity.setModelName(request.modelName().trim());
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        entity.setUpdatedAt(LocalDateTime.now());

        if (existing == null) {
            userAiConfigMapper.insert(entity);
        } else {
            userAiConfigMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConfigTestVO testConfig(Long userId, AiConfigTestRequest request) {
        UserAiConfig existing = findByUserId(userId);
        String apiKey = resolveApiKey(request.apiKey(), existing);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("测试连接需要填写接入密钥");
        }

        try {
            aiProvider.testConnection(apiKey, request.modelName().trim());
            updateTestStatus(existing, userId, AiRecordStatus.SUCCESS.name(), "连接成功");
            return new AiConfigTestVO(true, "连接成功");
        } catch (BusinessException ex) {
            updateTestStatus(existing, userId, AiRecordStatus.FAILED.name(), ex.getMessage());
            throw ex;
        }
    }

    @Override
    public String getActiveApiKey(Long userId) {
        UserAiConfig config = requiredEnabledConfig(userId);
        return aiApiKeyCipher.decrypt(config.getApiKeyEncrypted());
    }

    @Override
    public String getActiveModelName(Long userId) {
        return requiredEnabledConfig(userId).getModelName();
    }

    private UserAiConfig requiredEnabledConfig(Long userId) {
        UserAiConfig config = findByUserId(userId);
        if (config == null) {
            throw new BusinessException(400, "尚未配置 AI，请先在设置页保存接入信息");
        }
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException(400, "AI 助手未启用，请先在设置中打开开关");
        }
        return config;
    }

    private UserAiConfig findByUserId(Long userId) {
        return userAiConfigMapper.selectOne(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId)
                .last("limit 1"));
    }

    private String resolveApiKey(String requestedApiKey, UserAiConfig existing) {
        if (StringUtils.hasText(requestedApiKey)) {
            return requestedApiKey.trim();
        }
        if (existing != null) {
            return aiApiKeyCipher.decrypt(existing.getApiKeyEncrypted());
        }
        return null;
    }

    private void updateTestStatus(UserAiConfig existing, Long userId, String status, String message) {
        UserAiConfig target = existing == null ? findByUserId(userId) : existing;
        if (target == null) {
            return;
        }
        target.setLastTestStatus(status);
        target.setLastTestMessage(limitMessage(message));
        target.setUpdatedAt(LocalDateTime.now());
        userAiConfigMapper.updateById(target);
    }

    private String limitMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return message;
        }
        return message.length() > 255 ? message.substring(0, 255) : message;
    }
}

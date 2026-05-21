package com.practice.mailsystem.ai.service;

import com.practice.mailsystem.ai.dto.AiConfigSaveRequest;
import com.practice.mailsystem.ai.dto.AiConfigTestRequest;
import com.practice.mailsystem.ai.vo.AiConfigTestVO;
import com.practice.mailsystem.ai.vo.AiConfigVO;

public interface AiConfigService {

    AiConfigVO getCurrentUserConfig(Long userId);

    void saveOrUpdateConfig(Long userId, AiConfigSaveRequest request);

    AiConfigTestVO testConfig(Long userId, AiConfigTestRequest request);

    String getActiveApiKey(Long userId);

    String getActiveModelName(Long userId);
}

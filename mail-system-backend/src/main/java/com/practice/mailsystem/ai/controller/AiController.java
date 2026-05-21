package com.practice.mailsystem.ai.controller;

import com.practice.mailsystem.ai.dto.AiConfigSaveRequest;
import com.practice.mailsystem.ai.dto.AiConfigTestRequest;
import com.practice.mailsystem.ai.dto.ActionItemsRequest;
import com.practice.mailsystem.ai.dto.MailSummaryRequest;
import com.practice.mailsystem.ai.dto.ReplySuggestionRequest;
import com.practice.mailsystem.ai.service.AiConfigService;
import com.practice.mailsystem.ai.service.AiMailService;
import com.practice.mailsystem.ai.vo.AiConfigTestVO;
import com.practice.mailsystem.ai.vo.AiConfigVO;
import com.practice.mailsystem.ai.vo.ActionItemsVO;
import com.practice.mailsystem.ai.vo.MailSummaryVO;
import com.practice.mailsystem.ai.vo.ReplySuggestionVO;
import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiConfigService aiConfigService;
    private final AiMailService aiMailService;

    public AiController(AiConfigService aiConfigService, AiMailService aiMailService) {
        this.aiConfigService = aiConfigService;
        this.aiMailService = aiMailService;
    }

    @GetMapping("/config")
    public ApiResponse<AiConfigVO> getConfig() {
        return ApiResponse.ok(aiConfigService.getCurrentUserConfig(requireUserId()));
    }

    @PutMapping("/config")
    public ApiResponse<Void> saveConfig(@Valid @RequestBody AiConfigSaveRequest request) {
        aiConfigService.saveOrUpdateConfig(requireUserId(), request);
        return ApiResponse.ok();
    }

    @PostMapping("/config/test")
    public ApiResponse<AiConfigTestVO> testConfig(@Valid @RequestBody AiConfigTestRequest request) {
        return ApiResponse.ok(aiConfigService.testConfig(requireUserId(), request));
    }

    @PostMapping("/mail-summary")
    public ApiResponse<MailSummaryVO> summarize(@Valid @RequestBody MailSummaryRequest request) {
        return ApiResponse.ok(aiMailService.generateSummary(requireUserId(), request));
    }

    @PostMapping("/reply-suggestions")
    public ApiResponse<ReplySuggestionVO> suggestReplies(@Valid @RequestBody ReplySuggestionRequest request) {
        return ApiResponse.ok(aiMailService.generateReplySuggestions(requireUserId(), request));
    }

    @PostMapping("/action-items")
    public ApiResponse<ActionItemsVO> extractActionItems(@Valid @RequestBody ActionItemsRequest request) {
        return ApiResponse.ok(aiMailService.generateActionItems(requireUserId(), request));
    }

    private Long requireUserId() {
        Long userId = UserContext.requireUserId();
        if (userId == null) {
            throw new BusinessException(401, "Not logged in or session expired.");
        }
        return userId;
    }
}

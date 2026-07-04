package com.practice.mailsystem.ai.controller;

import com.practice.mailsystem.ai.dto.ActionItemsRequest;
import com.practice.mailsystem.ai.dto.AiConfigSaveRequest;
import com.practice.mailsystem.ai.dto.AiConfigTestRequest;
import com.practice.mailsystem.ai.dto.ChatRequest;
import com.practice.mailsystem.ai.dto.MailSummaryRequest;
import com.practice.mailsystem.ai.dto.ReplySuggestionRequest;
import com.practice.mailsystem.ai.service.AiConfigService;
import com.practice.mailsystem.ai.service.AiMailService;
import com.practice.mailsystem.ai.vo.ActionItemsVO;
import com.practice.mailsystem.ai.vo.AiConfigTestVO;
import com.practice.mailsystem.ai.vo.AiConfigVO;
import com.practice.mailsystem.ai.vo.ChatResponse;
import com.practice.mailsystem.ai.vo.MailSummaryVO;
import com.practice.mailsystem.ai.vo.ReplySuggestionVO;
import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.ApiResponse;
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
        return ApiResponse.ok(aiConfigService.getCurrentUserConfig(UserContext.requireUserId()));
    }

    @PutMapping("/config")
    public ApiResponse<Void> saveConfig(@Valid @RequestBody AiConfigSaveRequest request) {
        aiConfigService.saveOrUpdateConfig(UserContext.requireUserId(), request);
        return ApiResponse.ok();
    }

    @PostMapping("/config/test")
    public ApiResponse<AiConfigTestVO> testConfig(@Valid @RequestBody AiConfigTestRequest request) {
        return ApiResponse.ok(aiConfigService.testConfig(UserContext.requireUserId(), request));
    }

    @PostMapping("/mail-summary")
    public ApiResponse<MailSummaryVO> summarize(@Valid @RequestBody MailSummaryRequest request) {
        return ApiResponse.ok(aiMailService.generateSummary(UserContext.requireUserId(), request));
    }

    @PostMapping("/reply-suggestions")
    public ApiResponse<ReplySuggestionVO> suggestReplies(@Valid @RequestBody ReplySuggestionRequest request) {
        return ApiResponse.ok(aiMailService.generateReplySuggestions(UserContext.requireUserId(), request));
    }

    @PostMapping("/action-items")
    public ApiResponse<ActionItemsVO> extractActionItems(@Valid @RequestBody ActionItemsRequest request) {
        return ApiResponse.ok(aiMailService.generateActionItems(UserContext.requireUserId(), request));
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(aiMailService.chat(UserContext.requireUserId(), request));
    }
}

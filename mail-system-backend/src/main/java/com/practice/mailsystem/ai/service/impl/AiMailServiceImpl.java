package com.practice.mailsystem.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.mailsystem.ai.config.AiProperties;
import com.practice.mailsystem.ai.dto.ActionItemsRequest;
import com.practice.mailsystem.ai.dto.MailSummaryRequest;
import com.practice.mailsystem.ai.dto.ReplySuggestionRequest;
import com.practice.mailsystem.ai.entity.MailAiRecord;
import com.practice.mailsystem.ai.enums.AiActionType;
import com.practice.mailsystem.ai.enums.AiRecordStatus;
import com.practice.mailsystem.ai.mapper.MailAiRecordMapper;
import com.practice.mailsystem.ai.provider.AiProvider;
import com.practice.mailsystem.ai.service.AiConfigService;
import com.practice.mailsystem.ai.service.AiMailService;
import com.practice.mailsystem.ai.vo.ActionItemVO;
import com.practice.mailsystem.ai.vo.ActionItemsVO;
import com.practice.mailsystem.ai.vo.MailSummaryVO;
import com.practice.mailsystem.ai.vo.ReplySuggestionVO;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.mail.entity.MailMessage;
import com.practice.mailsystem.mail.entity.MailUserBox;
import com.practice.mailsystem.mail.mapper.MailMessageMapper;
import com.practice.mailsystem.mail.mapper.MailUserBoxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AiMailServiceImpl implements AiMailService {

    private final MailMessageMapper mailMessageMapper;
    private final MailUserBoxMapper mailUserBoxMapper;
    private final MailAiRecordMapper mailAiRecordMapper;
    private final AiConfigService aiConfigService;
    private final AiProvider aiProvider;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiMailServiceImpl(MailMessageMapper mailMessageMapper,
                             MailUserBoxMapper mailUserBoxMapper,
                             MailAiRecordMapper mailAiRecordMapper,
                             AiConfigService aiConfigService,
                             AiProvider aiProvider,
                             AiProperties aiProperties,
                             ObjectMapper objectMapper) {
        this.mailMessageMapper = mailMessageMapper;
        this.mailUserBoxMapper = mailUserBoxMapper;
        this.mailAiRecordMapper = mailAiRecordMapper;
        this.aiConfigService = aiConfigService;
        this.aiProvider = aiProvider;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MailSummaryVO generateSummary(Long userId, MailSummaryRequest request) {
        MailContent mailContent = loadAccessibleMail(userId, request.mailId());
        String apiKey = aiConfigService.getActiveApiKey(userId);
        String modelName = aiConfigService.getActiveModelName(userId);
        String promptSnapshot = buildPromptSnapshot(mailContent.subject(), mailContent.content());
        try {
            String summary = aiProvider.summarize(mailContent.subject(), mailContent.content(), apiKey, modelName);
            saveRecord(userId, request.mailId(), AiActionType.SUMMARY, promptSnapshot,
                    serializeResult(Map.of("summary", summary)), AiRecordStatus.SUCCESS, null);
            return new MailSummaryVO(request.mailId(), summary);
        } catch (BusinessException ex) {
            saveRecord(userId, request.mailId(), AiActionType.SUMMARY, promptSnapshot,
                    null, AiRecordStatus.FAILED, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            saveRecord(userId, request.mailId(), AiActionType.SUMMARY, promptSnapshot,
                    null, AiRecordStatus.FAILED, ex.getMessage());
            throw new BusinessException(500, "Summary generation failed. Please try again later.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReplySuggestionVO generateReplySuggestions(Long userId, ReplySuggestionRequest request) {
        MailContent mailContent = loadAccessibleMail(userId, request.mailId());
        String apiKey = aiConfigService.getActiveApiKey(userId);
        String modelName = aiConfigService.getActiveModelName(userId);
        String promptSnapshot = buildPromptSnapshot(mailContent.subject(), mailContent.content());
        try {
            List<String> suggestions = aiProvider.suggestReplies(
                    mailContent.subject(),
                    mailContent.content(),
                    request.tone(),
                    apiKey,
                    modelName
            );
            saveRecord(userId, request.mailId(), AiActionType.REPLY_SUGGESTION, promptSnapshot,
                    serializeResult(Map.of("suggestions", suggestions, "tone", request.tone())),
                    AiRecordStatus.SUCCESS, null);
            return new ReplySuggestionVO(request.mailId(), suggestions);
        } catch (BusinessException ex) {
            saveRecord(userId, request.mailId(), AiActionType.REPLY_SUGGESTION, promptSnapshot,
                    null, AiRecordStatus.FAILED, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            saveRecord(userId, request.mailId(), AiActionType.REPLY_SUGGESTION, promptSnapshot,
                    null, AiRecordStatus.FAILED, ex.getMessage());
            throw new BusinessException(500, "Reply suggestion generation failed. Please try again later.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActionItemsVO generateActionItems(Long userId, ActionItemsRequest request) {
        MailContent mailContent = loadAccessibleMail(userId, request.mailId());
        String apiKey = aiConfigService.getActiveApiKey(userId);
        String modelName = aiConfigService.getActiveModelName(userId);
        String promptSnapshot = buildPromptSnapshot(mailContent.subject(), mailContent.content());
        try {
            List<ActionItemVO> items = aiProvider.extractActionItems(
                    mailContent.subject(),
                    mailContent.content(),
                    apiKey,
                    modelName
            );
            saveRecord(userId, request.mailId(), AiActionType.ACTION_ITEMS, promptSnapshot,
                    serializeResult(Map.of("items", items)), AiRecordStatus.SUCCESS, null);
            return new ActionItemsVO(request.mailId(), items);
        } catch (BusinessException ex) {
            saveRecord(userId, request.mailId(), AiActionType.ACTION_ITEMS, promptSnapshot,
                    null, AiRecordStatus.FAILED, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            saveRecord(userId, request.mailId(), AiActionType.ACTION_ITEMS, promptSnapshot,
                    null, AiRecordStatus.FAILED, ex.getMessage());
            throw new BusinessException(500, "Action item extraction failed. Please try again later.");
        }
    }

    private MailContent loadAccessibleMail(Long userId, Long mailId) {
        MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getMailId, mailId)
                .last("limit 1"));
        if (box == null) {
            throw new BusinessException(404, "Mail not found or access denied.");
        }
        MailMessage message = mailMessageMapper.selectById(mailId);
        if (message == null) {
            throw new BusinessException(404, "Mail content not found.");
        }
        String content = StringUtils.hasText(message.getContentText())
                ? message.getContentText()
                : stripHtml(message.getContentHtml());
        content = normalizeContent(content);
        return new MailContent(message.getSubject(), content);
    }

    private String normalizeContent(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        int maxLength = Math.max(aiProperties.maxContentLength(), 500);
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private void saveRecord(Long userId,
                            Long mailId,
                            AiActionType actionType,
                            String promptSnapshot,
                            String resultJson,
                            AiRecordStatus status,
                            String errorMessage) {
        MailAiRecord record = new MailAiRecord();
        record.setUserId(userId);
        record.setMailId(mailId);
        record.setActionType(actionType.name());
        record.setPromptSnapshot(promptSnapshot);
        record.setResultJson(resultJson);
        record.setStatus(status.name());
        record.setErrorMessage(limitMessage(errorMessage));
        record.setCreatedAt(LocalDateTime.now());
        mailAiRecordMapper.insert(record);
    }

    private String buildPromptSnapshot(String subject, String content) {
        return "subject=" + subject + "\ncontent=" + content;
    }

    private String serializeResult(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"status\":\"serialization_failed\"}";
        }
    }

    private String limitMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return message;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private record MailContent(String subject, String content) {
    }
}

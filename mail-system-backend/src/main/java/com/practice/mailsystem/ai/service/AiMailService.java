package com.practice.mailsystem.ai.service;

import com.practice.mailsystem.ai.dto.ActionItemsRequest;
import com.practice.mailsystem.ai.dto.ChatRequest;
import com.practice.mailsystem.ai.dto.MailSummaryRequest;
import com.practice.mailsystem.ai.dto.ReplySuggestionRequest;
import com.practice.mailsystem.ai.vo.ActionItemsVO;
import com.practice.mailsystem.ai.vo.ChatResponse;
import com.practice.mailsystem.ai.vo.MailSummaryVO;
import com.practice.mailsystem.ai.vo.ReplySuggestionVO;

public interface AiMailService {

    MailSummaryVO generateSummary(Long userId, MailSummaryRequest request);

    ReplySuggestionVO generateReplySuggestions(Long userId, ReplySuggestionRequest request);

    ActionItemsVO generateActionItems(Long userId, ActionItemsRequest request);

    ChatResponse chat(Long userId, ChatRequest request);
}

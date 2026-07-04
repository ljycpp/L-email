package com.practice.mailsystem.ai.provider;

import com.practice.mailsystem.ai.vo.ActionItemVO;
import com.practice.mailsystem.ai.vo.MailPriorityResult;

import java.util.List;

public interface AiProvider {

    void testConnection(String apiKey, String modelName);

    String summarize(String subject, String content, String apiKey, String modelName);

    List<String> suggestReplies(String subject, String content, String tone, String apiKey, String modelName);

    List<ActionItemVO> extractActionItems(String subject, String content, String apiKey, String modelName);

    MailPriorityResult classifyPriority(String senderName,
                                        String senderMail,
                                        String subject,
                                        String content,
                                        String apiKey,
                                        String modelName);

    String chat(String userMessage, String mailContext, String apiKey, String modelName);
}

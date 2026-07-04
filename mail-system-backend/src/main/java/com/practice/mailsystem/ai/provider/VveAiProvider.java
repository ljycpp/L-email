package com.practice.mailsystem.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.mailsystem.ai.config.AiProperties;
import com.practice.mailsystem.ai.prompt.AiPromptBuilder;
import com.practice.mailsystem.ai.vo.ActionItemVO;
import com.practice.mailsystem.ai.vo.MailPriorityResult;
import com.practice.mailsystem.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class VveAiProvider implements AiProvider {

    private final AiProperties aiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public VveAiProvider(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(aiProperties.connectTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(aiProperties.readTimeoutSeconds() * 1000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void testConnection(String apiKey, String modelName) {
        executePrompt(
                apiKey,
                modelName,
                "You are a connectivity test assistant.",
                "Reply with OK only.",
                0.1
        );
    }

    @Override
    public String summarize(String subject, String content, String apiKey, String modelName) {
        return executePrompt(
                apiKey,
                modelName,
                AiPromptBuilder.summarySystemPrompt(),
                AiPromptBuilder.summaryUserPrompt(subject, content),
                0.3
        );
    }

    @Override
    public List<String> suggestReplies(String subject, String content, String tone, String apiKey, String modelName) {
        String result = executePrompt(
                apiKey,
                modelName,
                AiPromptBuilder.replySuggestionSystemPrompt(tone),
                AiPromptBuilder.replySuggestionUserPrompt(subject, content),
                0.7
        );
        return parseReplySuggestions(result);
    }

    @Override
    public List<ActionItemVO> extractActionItems(String subject, String content, String apiKey, String modelName) {
        String result = executePrompt(
                apiKey,
                modelName,
                AiPromptBuilder.actionItemsSystemPrompt(),
                AiPromptBuilder.actionItemsUserPrompt(subject, content),
                0.2
        );
        return parseActionItems(result);
    }

    @Override
    public MailPriorityResult classifyPriority(String senderName,
                                               String senderMail,
                                               String subject,
                                               String content,
                                               String apiKey,
                                               String modelName) {
        String result = executePrompt(
                apiKey,
                modelName,
                AiPromptBuilder.prioritySystemPrompt(),
                AiPromptBuilder.priorityUserPrompt(senderName, senderMail, subject, content),
                0.2
        );
        return parsePriorityResult(result);
    }

    @Override
    public String chat(String userMessage, String mailContext, String apiKey, String modelName) {
        boolean hasContext = mailContext != null && !mailContext.isBlank();
        return executePrompt(
                apiKey,
                modelName,
                AiPromptBuilder.chatSystemPrompt(hasContext),
                AiPromptBuilder.chatUserPrompt(userMessage, mailContext),
                0.7
        );
    }

    private String executePrompt(String apiKey,
                                 String modelName,
                                 String systemPrompt,
                                 String userPrompt,
                                 double temperature) {
        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", temperature,
                "stream", false
        );

        try {
            JsonNode response = restClient.post()
                    .uri(aiProperties.vveaiChatUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
            return extractContent(response);
        } catch (RestClientResponseException ex) {
            throw new BusinessException(502, buildRemoteErrorMessage(ex.getResponseBodyAsString()));
        } catch (ResourceAccessException ex) {
            throw new BusinessException(504, "AI request timed out. Please try again later.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(500, "AI request failed.");
        }
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            throw new BusinessException(502, "AI service returned an empty response.");
        }
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new BusinessException(502, "AI service did not return a valid completion.");
        }
        String content = choices.get(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new BusinessException(502, "AI service returned empty content.");
        }
        return content.trim();
    }

    private String buildRemoteErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "AI service returned an error.";
        }
        String compact = responseBody.replaceAll("\\s+", " ").trim();
        return compact.length() > 200 ? compact.substring(0, 200) : compact;
    }

    private List<String> parseReplySuggestions(String rawContent) {
        String[] lines = rawContent.split("\\r?\\n");
        List<String> suggestions = new ArrayList<>();
        for (String line : lines) {
            String normalized = normalizeBulletLine(line);
            if (!normalized.isBlank()) {
                suggestions.add(normalized);
            }
        }
        if (suggestions.isEmpty()) {
            suggestions.add(rawContent.trim());
        }
        return suggestions.size() > 3 ? suggestions.subList(0, 3) : suggestions;
    }

    private MailPriorityResult parsePriorityResult(String rawContent) {
        String compact = stripCodeFence(rawContent);
        try {
            JsonNode root = objectMapper.readTree(compact);
            String level = root.path("level").asText("MEDIUM").trim().toUpperCase();
            double score = root.path("score").asDouble(0.5);
            String reason = root.path("reason").asText("").trim();
            if (!level.equals("HIGH") && !level.equals("LOW")) {
                level = "MEDIUM";
            }
            if (reason.isBlank()) {
                reason = "系统已根据邮件内容评估优先级";
            }
            return new MailPriorityResult(level, score, reason);
        } catch (Exception ignored) {
            // Fall through.
        }
        return new MailPriorityResult("MEDIUM", 0.5, compact.length() > 200 ? compact.substring(0, 200) : compact);
    }

    private List<ActionItemVO> parseActionItems(String rawContent) {
        String compact = stripCodeFence(rawContent);
        try {
            JsonNode root = objectMapper.readTree(compact);
            if (root.isArray()) {
                List<ActionItemVO> items = new ArrayList<>();
                for (JsonNode node : root) {
                    String task = node.path("task").asText("").trim();
                    String deadline = node.path("deadline").asText("").trim();
                    List<String> contacts = parseContactsNode(node.path("contacts"));
                    if (!task.isBlank()) {
                        items.add(new ActionItemVO(task, deadline, contacts));
                    }
                }
                if (!items.isEmpty()) {
                    return items.size() > 5 ? items.subList(0, 5) : items;
                }
            }
        } catch (Exception ignored) {
            // Fall back to line parsing.
        }
        return parseActionItemsFromLines(compact);
    }

    private List<String> parseContactsNode(JsonNode contactsNode) {
        if (contactsNode == null || contactsNode.isMissingNode() || contactsNode.isNull()) {
            return Collections.emptyList();
        }
        if (contactsNode.isArray()) {
            List<String> contacts = new ArrayList<>();
            contactsNode.forEach(node -> {
                String value = node.asText("").trim();
                if (!value.isBlank()) {
                    contacts.add(value);
                }
            });
            return contacts;
        }
        String raw = contactsNode.asText("").trim();
        if (raw.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = raw.split("[,，/;；]");
        List<String> contacts = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.isBlank()) {
                contacts.add(value);
            }
        }
        return contacts;
    }

    private List<ActionItemVO> parseActionItemsFromLines(String rawContent) {
        List<ActionItemVO> items = new ArrayList<>();
        String[] lines = rawContent.split("\\r?\\n");
        for (String line : lines) {
            String normalized = normalizeBulletLine(line);
            if (normalized.isBlank()) {
                continue;
            }
            String[] parts = normalized.split("\\|");
            String task = parts.length > 0 ? parts[0].trim() : normalized;
            String deadline = parts.length > 1 ? parts[1].trim() : "";
            List<String> contacts = parts.length > 2
                    ? parseContactsNode(objectMapper.getNodeFactory().textNode(parts[2]))
                    : Collections.emptyList();
            if (!task.isBlank()) {
                items.add(new ActionItemVO(task, deadline, contacts));
            }
            if (items.size() >= 5) {
                break;
            }
        }
        if (!items.isEmpty()) {
            return items;
        }
        return List.of(new ActionItemVO(rawContent.trim(), "", Collections.emptyList()));
    }

    private String normalizeBulletLine(String line) {
        String normalized = line == null ? "" : line.trim();
        normalized = normalized.replaceFirst("^[-*•]+\\s*", "");
        normalized = normalized.replaceFirst("^\\d+[.)]\\s*", "");
        return normalized;
    }

    private String stripCodeFence(String value) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replaceFirst("^```json\\s*", "");
        normalized = normalized.replaceFirst("^```\\s*", "");
        normalized = normalized.replaceFirst("\\s*```$", "");
        return normalized.trim();
    }
}

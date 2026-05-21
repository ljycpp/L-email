package com.practice.mailsystem.ai.prompt;

public final class AiPromptBuilder {

    private AiPromptBuilder() {
    }

    public static String summarySystemPrompt() {
        return "You are an email assistant. Generate a concise summary based only on the email subject and body. "
                + "Do not invent details. Keep it within 80 to 120 Chinese characters when possible.";
    }

    public static String summaryUserPrompt(String subject, String content) {
        return "Email subject:\n" + subject + "\n\nEmail content:\n" + content + "\n\nPlease summarize the key points.";
    }

    public static String replySuggestionSystemPrompt(String tone) {
        return "You are an email reply assistant. Generate exactly 3 reply suggestions for the current email. "
                + "Each suggestion must be a complete reply and should match the requested tone: " + normalizeTone(tone) + ". "
                + "Return each suggestion on a separate line with no numbering and no extra explanation.";
    }

    public static String replySuggestionUserPrompt(String subject, String content) {
        return "Email subject:\n" + subject + "\n\nEmail content:\n" + content + "\n\nGenerate 3 reply suggestions.";
    }

    public static String actionItemsSystemPrompt() {
        return "You are an email task extraction assistant. Extract 3 to 5 actionable checklist items from the current email when possible. "
                + "Return strict JSON only with this shape: "
                + "[{\"task\":\"...\",\"deadline\":\"...\",\"contacts\":[\"...\"]}]. "
                + "Use an empty string when deadline is unknown, use an empty array when contacts are unknown, and do not include any markdown fences.";
    }

    public static String actionItemsUserPrompt(String subject, String content) {
        return "Email subject:\n" + subject + "\n\nEmail content:\n" + content
                + "\n\nExtract the actionable tasks, deadline, and key contacts.";
    }

    public static String prioritySystemPrompt() {
        return "你是企业邮箱的优先级评估助手。仅根据邮件主题和正文判断处理优先级，不要编造未出现的信息。"
                + "规则：level 只能是 HIGH、MEDIUM、LOW；score 为 0 到 1 的小数，HIGH 通常 >= 0.75，LOW 通常 <= 0.4。"
                + "HIGH：明确紧迫截止时间、上级或客户投诉、安全付款合同、需立即行动。"
                + "MEDIUM：一般工作协调、本周内需处理。"
                + "LOW：订阅促销、抄送阅知、无明确行动要求。"
                + "reason 用中文 1 到 2 句话说明依据，不超过 80 字。"
                + "只输出一行合法 JSON，不要 markdown。格式：{\"level\":\"HIGH\",\"score\":0.85,\"reason\":\"...\"}";
    }

    public static String priorityUserPrompt(String senderName, String senderMail, String subject, String content) {
        return "请评估以下邮件的处理优先级。\n\n"
                + "发件人：" + senderName + " <" + senderMail + ">\n"
                + "主题：" + subject + "\n"
                + "正文：\n" + content;
    }

    private static String normalizeTone(String tone) {
        return switch (tone) {
            case "brief" -> "brief and efficient";
            case "polite" -> "polite and considerate";
            case "friendly" -> "friendly and warm";
            default -> "formal and professional";
        };
    }
}

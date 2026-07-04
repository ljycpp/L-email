package com.practice.mailsystem.ai.prompt;

public final class AiPromptBuilder {

    private AiPromptBuilder() {
    }

    public static String summarySystemPrompt() {
        return "你是一位专业的邮件助手，擅长提炼邮件核心信息。"
                + "请根据邮件主题和正文生成简洁的中文摘要，重点概括：事件背景、关键信息、需要关注的要点。"
                + "要求：仅使用邮件中实际出现的内容，不编造信息；摘要控制在 100～150 字以内；使用流畅自然的中文。";
    }

    public static String summaryUserPrompt(String subject, String content) {
        return "邮件主题：" + subject + "\n\n邮件正文：\n" + content + "\n\n请生成这封邮件的核心摘要。";
    }

    public static String replySuggestionSystemPrompt(String tone) {
        return "你是一位专业的邮件回复助手。请为当前邮件生成恰好 3 条回复建议，每条都是完整可直接发送的回复内容。"
                + "回复风格要求：" + normalizeToneChinese(tone) + "。"
                + "要求：每条回复单独占一行，行与行之间用空行分隔；不加编号、不加额外说明；"
                + "回复要有针对性，体现对邮件内容的理解；使用地道的中文表达。";
    }

    public static String replySuggestionUserPrompt(String subject, String content) {
        return "邮件主题：" + subject + "\n\n邮件正文：\n" + content + "\n\n请生成 3 条回复建议。";
    }

    public static String actionItemsSystemPrompt() {
        return "你是一位专业的邮件任务提取助手。请从邮件中提取 3～5 条可执行的待办事项。"
                + "仅返回严格的 JSON 数组，格式：[{\"task\":\"任务描述\",\"deadline\":\"截止时间\",\"contacts\":[\"联系人\"]}]。"
                + "规则：task 字段用中文简要描述具体行动；deadline 字段填写邮件中明确提及的时间，无则用空字符串；"
                + "contacts 字段填写与任务相关的人名或邮箱，无则用空数组；不包含任何 markdown、代码块或额外说明。";
    }

    public static String actionItemsUserPrompt(String subject, String content) {
        return "邮件主题：" + subject + "\n\n邮件正文：\n" + content
                + "\n\n请提取待办任务、截止时间和关键联系人。";
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

    /**
     * 通用对话系统提示词，支持邮件上下文或纯对话模式。
     */
    public static String chatSystemPrompt(boolean hasMailContext) {
        String base = "你是一位智能邮件助手，由本邮件系统内置，可以像 ChatGPT 一样自由对话。"
                + "你熟悉商务沟通、邮件写作、信息整理等场景，能够帮助用户起草邮件、解释内容、回答问题或提供建议。"
                + "始终用中文回答，保持专业友好的风格，回答简洁明了。";
        if (hasMailContext) {
            return base + "用户当前正在查看一封邮件，以下对话可能与该邮件内容相关，请结合邮件上下文给出有针对性的回答。";
        }
        return base + "如果用户的问题涉及邮件操作，可以给出通用建议。";
    }

    /**
     * 通用对话用户提示词，附带可选的邮件上下文。
     */
    public static String chatUserPrompt(String userMessage, String mailContext) {
        if (mailContext == null || mailContext.isBlank()) {
            return userMessage;
        }
        return "【当前邮件上下文】\n" + mailContext + "\n\n【用户问题】\n" + userMessage;
    }

    private static String normalizeToneChinese(String tone) {
        return switch (tone) {
            case "brief" -> "简洁高效，语言精炼，直击重点";
            case "polite" -> "礼貌周到，措辞得体，表达尊重";
            case "friendly" -> "友好亲切，语气轻松，拉近距离";
            default -> "正式专业，措辞严谨，符合商务规范";
        };
    }
}

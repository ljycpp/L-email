package com.practice.mailsystem.mail.util;

/**
 * 邮箱工具方法：boxType 规范化、HTML 剥离。
 */
public final class MailBoxUtils {

    private MailBoxUtils() {
    }

    /**
     * 将前端传入的各种 boxType 别名统一为标准值（INBOX/OUTBOX/DRAFT），
     * 无法识别时返回 null（不限制箱型）。
     */
    public static String normalizeBoxType(String boxType) {
        if (boxType == null || boxType.isBlank()) {
            return null;
        }
        return switch (boxType.trim().toUpperCase()) {
            case "INBOX", "RECEIVE" -> "INBOX";
            case "OUTBOX", "SEND"   -> "OUTBOX";
            case "DRAFT", "DRAFTBOX" -> "DRAFT";
            default -> null;
        };
    }

    /**
     * 简单剥除 HTML 标签，用于生成纯文本摘要。
     */
    public static String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }
}

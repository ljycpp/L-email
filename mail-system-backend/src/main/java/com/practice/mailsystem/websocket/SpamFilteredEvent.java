package com.practice.mailsystem.websocket;

public record SpamFilteredEvent(
        String type,
        Long mailId,
        String title,
        String sender,
        String senderMail,
        String reason
) {
    public static SpamFilteredEvent of(Long mailId, String title, String sender, String senderMail, String reason) {
        return new SpamFilteredEvent("SPAM_FILTERED", mailId, title, sender, senderMail, reason);
    }
}

package com.practice.mailsystem.websocket;

public record NewMailEvent(
        String type,
        Long mailId,
        String title,
        String sender,
        String senderMail,
        String priorityLevel,
        Double priorityScore
) {
    public static NewMailEvent of(Long mailId, String title, String sender, String senderMail) {
        return new NewMailEvent("NEW_MAIL", mailId, title, sender, senderMail, null, null);
    }

    public static NewMailEvent of(Long mailId,
                                  String title,
                                  String sender,
                                  String senderMail,
                                  String priorityLevel,
                                  Double priorityScore) {
        return new NewMailEvent("NEW_MAIL", mailId, title, sender, senderMail, priorityLevel, priorityScore);
    }
}

package com.practice.mailsystem.websocket;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class MailNotificationService {

    private final MailWebSocketHandler mailWebSocketHandler;

    public MailNotificationService(MailWebSocketHandler mailWebSocketHandler) {
        this.mailWebSocketHandler = mailWebSocketHandler;
    }

    public void notifyNewMail(Collection<Long> recipientUserIds, Long mailId, String title, String sender, String senderMail) {
        notifyNewMail(recipientUserIds, mailId, title, sender, senderMail, null, null);
    }

    public void notifyNewMail(Collection<Long> recipientUserIds,
                              Long mailId,
                              String title,
                              String sender,
                              String senderMail,
                              String priorityLevel,
                              Double priorityScore) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueRecipients = new LinkedHashSet<>(recipientUserIds);
        NewMailEvent event = NewMailEvent.of(mailId, title, sender, senderMail, priorityLevel, priorityScore);
        for (Long recipientUserId : uniqueRecipients) {
            if (recipientUserId != null) {
                mailWebSocketHandler.sendToUser(recipientUserId, event);
            }
        }
    }

    public void notifyNewMail(Long recipientUserId, Long mailId, String title, String sender, String senderMail) {
        notifyNewMail(recipientUserId, mailId, title, sender, senderMail, null, null);
    }

    public void notifyNewMail(Long recipientUserId,
                              Long mailId,
                              String title,
                              String sender,
                              String senderMail,
                              String priorityLevel,
                              Double priorityScore) {
        if (recipientUserId == null) {
            return;
        }
        mailWebSocketHandler.sendToUser(
                recipientUserId,
                NewMailEvent.of(mailId, title, sender, senderMail, priorityLevel, priorityScore)
        );
    }

    public void notifySpamFiltered(Long recipientUserId,
                                   Long mailId,
                                   String title,
                                   String sender,
                                   String senderMail,
                                   String reason) {
        if (recipientUserId == null) {
            return;
        }
        mailWebSocketHandler.sendToUser(
                recipientUserId,
                SpamFilteredEvent.of(mailId, title, sender, senderMail, reason)
        );
    }
}

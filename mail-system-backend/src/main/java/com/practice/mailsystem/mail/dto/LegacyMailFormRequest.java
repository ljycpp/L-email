package com.practice.mailsystem.mail.dto;

import java.util.ArrayList;
import java.util.List;

public record LegacyMailFormRequest(
        Long draftId,
        String title,
        String content,
        List<String> targets,
        List<String> copies,
        List<Long> attachmentIds
) {
    public List<String> safeTargets() {
        return targets == null ? new ArrayList<>() : targets;
    }

    public List<String> safeCopies() {
        return copies == null ? new ArrayList<>() : copies;
    }

    public List<Long> safeAttachmentIds() {
        return attachmentIds == null ? new ArrayList<>() : attachmentIds;
    }
}

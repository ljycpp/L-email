package com.practice.mailsystem.mail.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public record MailCreateRequest(
        Long draftId,
        @NotBlank String title,
        @NotBlank String content,
        @Valid List<PartyRequest> target,
        @Valid List<PartyRequest> copy,
        List<Long> attachmentIds
) {
    public List<PartyRequest> safeTarget() {
        return target == null ? new ArrayList<>() : target;
    }

    public List<PartyRequest> safeCopy() {
        return copy == null ? new ArrayList<>() : copy;
    }

    public List<Long> safeAttachmentIds() {
        return attachmentIds == null ? new ArrayList<>() : attachmentIds;
    }
}

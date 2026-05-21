package com.practice.mailsystem.ai.dto;

import jakarta.validation.constraints.NotNull;

public record MailSummaryRequest(
        @NotNull Long mailId
) {
}

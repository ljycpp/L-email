package com.practice.mailsystem.mail.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record LabelMarkRequest(
        @NotNull Long labelId,
        @NotEmpty List<Long> mailIds
) {
}

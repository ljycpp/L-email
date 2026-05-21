package com.practice.mailsystem.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReplySuggestionRequest(
        @NotNull Long mailId,
        @NotBlank
        @Pattern(regexp = "formal|brief|polite|friendly", message = "Invalid tone")
        String tone
) {
}

package com.practice.mailsystem.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiConfigSaveRequest(
        @Size(max = 255) String apiKey,
        @NotBlank @Size(max = 100) String modelName,
        @NotNull Boolean enabled
) {
}

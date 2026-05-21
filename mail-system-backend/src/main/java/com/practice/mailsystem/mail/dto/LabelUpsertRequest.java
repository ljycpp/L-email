package com.practice.mailsystem.mail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LabelUpsertRequest(
        Long id,
        @NotBlank @Size(max = 30) String name,
        @NotBlank @Size(max = 30) String color
) {
}

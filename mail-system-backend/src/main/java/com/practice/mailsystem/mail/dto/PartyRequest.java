package com.practice.mailsystem.mail.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PartyRequest(
        @NotBlank String name,
        @NotBlank @Email String mail
) {
}

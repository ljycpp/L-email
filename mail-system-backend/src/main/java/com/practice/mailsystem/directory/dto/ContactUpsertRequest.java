package com.practice.mailsystem.directory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactUpsertRequest(
        Long id,
        Long groupId,
        @NotBlank @Size(max = 30) String name,
        @NotBlank @Email String mail,
        String avatarUrl,
        String remark
) {
}

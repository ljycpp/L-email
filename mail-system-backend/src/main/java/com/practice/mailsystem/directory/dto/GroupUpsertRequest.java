package com.practice.mailsystem.directory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public record GroupUpsertRequest(
        Long id,
        @NotBlank @Size(max = 30) String name,
        List<String> contacts
) {
    public List<String> safeContacts() {
        return contacts == null ? new ArrayList<>() : contacts;
    }
}

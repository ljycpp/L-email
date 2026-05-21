package com.practice.mailsystem.ai.vo;

import java.util.List;

public record ActionItemVO(
        String task,
        String deadline,
        List<String> contacts
) {
}

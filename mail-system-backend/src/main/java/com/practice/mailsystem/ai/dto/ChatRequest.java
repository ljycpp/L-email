package com.practice.mailsystem.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容不能超过 2000 字符")
        String message,
        Long mailId
) {
}

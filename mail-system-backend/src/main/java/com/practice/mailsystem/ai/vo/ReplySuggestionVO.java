package com.practice.mailsystem.ai.vo;

import java.util.List;

public record ReplySuggestionVO(
        Long mailId,
        List<String> suggestions
) {
}

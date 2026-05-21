package com.practice.mailsystem.ai.vo;

import java.util.List;

public record ActionItemsVO(
        Long mailId,
        List<ActionItemVO> items
) {
}

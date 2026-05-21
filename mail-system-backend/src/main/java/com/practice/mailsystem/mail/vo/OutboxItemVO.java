package com.practice.mailsystem.mail.vo;

import java.time.LocalDateTime;
import java.util.List;

public record OutboxItemVO(
        Long id,
        boolean isStar,
        boolean isHaveFile,
        boolean isHaveAudio,
        List<PartyVO> receiveList,
        List<LabelItemVO> labelList,
        String title,
        LocalDateTime sendDate
) {
}

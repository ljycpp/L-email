package com.practice.mailsystem.mail.vo;

import java.time.LocalDateTime;
import java.util.List;

public record InboxItemVO(
        Long id,
        boolean isStar,
        boolean isHaveFile,
        boolean isHaveAudio,
        int status,
        String sendName,
        String sendMail,
        List<LabelItemVO> labelList,
        String title,
        String preview,
        LocalDateTime receiveDate,
        LocalDateTime readDate,
        String spamReason,
        Double spamScore,
        String priorityLevel,
        Double priorityScore,
        String priorityReason
) {
}

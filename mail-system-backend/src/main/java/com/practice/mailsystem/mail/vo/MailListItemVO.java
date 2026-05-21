package com.practice.mailsystem.mail.vo;

import java.time.LocalDateTime;
import java.util.List;

public record MailListItemVO(
        Long id,
        boolean isStar,
        boolean isHaveFile,
        boolean isHaveAudio,
        String type,
        String sendName,
        String sendMail,
        List<LabelItemVO> labelList,
        String title,
        LocalDateTime date
) {
}

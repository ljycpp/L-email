package com.practice.mailsystem.mail.vo;

import java.time.LocalDateTime;
import java.util.List;

public record MailDetailVO(
        Long id,
        String title,
        String content,
        String sender,
        String sendMail,
        LocalDateTime receiveDate,
        LocalDateTime sendDate,
        boolean isStar,
        List<PartyVO> target,
        List<PartyVO> copy,
        List<AttachmentItemVO> oldFileList,
        List<AttachmentItemVO> oldAudioList,
        List<LabelItemVO> labelList,
        String spamReason,
        Double spamScore,
        boolean autoFiltered,
        String priorityLevel,
        Double priorityScore,
        String priorityReason,
        boolean priorityScored
) {
}

package com.practice.mailsystem.mail.vo;

import java.time.LocalDateTime;
import java.util.List;

public record DraftItemVO(
        Long id,
        boolean isStar,
        boolean isHaveFile,
        boolean isHaveAudio,
        List<PartyVO> receiveList,
        List<LabelItemVO> labelList,
        String title,
        LocalDateTime createDate,
        LocalDateTime lastModifyDate
) {
}

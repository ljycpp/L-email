package com.practice.mailsystem.mail.vo;

public record AttachmentItemVO(
        Long id,
        String name,
        String url,
        Long size,
        String contentType
) {
}

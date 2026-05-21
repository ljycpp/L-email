package com.practice.mailsystem.attachment.vo;

public record AttachmentUploadResponse(Long id, String name, String url, Long size, String contentType) {
}

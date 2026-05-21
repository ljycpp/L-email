package com.practice.mailsystem.attachment.service;

import com.practice.mailsystem.attachment.entity.MailAttachment;
import com.practice.mailsystem.attachment.vo.AttachmentUploadResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {

    AttachmentUploadResponse upload(MultipartFile file);

    MailAttachment getById(Long id);

    Resource loadAsResource(Long id);

    /** 将已有附件复制到新邮件（转发场景），返回新附件 id */
    Long cloneForMail(Long sourceAttachmentId, Long targetMailId, Long userId);
}

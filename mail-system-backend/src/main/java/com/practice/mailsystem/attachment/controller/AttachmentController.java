package com.practice.mailsystem.attachment.controller;

import com.practice.mailsystem.attachment.entity.MailAttachment;
import com.practice.mailsystem.attachment.service.AttachmentService;
import com.practice.mailsystem.attachment.vo.AttachmentUploadResponse;
import com.practice.mailsystem.common.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/upload")
    public ApiResponse<AttachmentUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(attachmentService.upload(file));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        MailAttachment attachment = attachmentService.getById(id);
        Resource resource = attachmentService.loadAsResource(id);
        String encodedName = URLEncoder.encode(attachment.getOriginalName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}

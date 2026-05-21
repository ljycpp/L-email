package com.practice.mailsystem.attachment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.mailsystem.attachment.entity.MailAttachment;
import com.practice.mailsystem.attachment.mapper.MailAttachmentMapper;
import com.practice.mailsystem.attachment.service.AttachmentService;
import com.practice.mailsystem.attachment.vo.AttachmentUploadResponse;
import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.PublicUrlBuilder;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.mail.entity.MailUserBox;
import com.practice.mailsystem.mail.mapper.MailUserBoxMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AttachmentServiceImpl implements AttachmentService {

    private final MailAttachmentMapper attachmentMapper;
    private final MailUserBoxMapper mailUserBoxMapper;
    private final PublicUrlBuilder publicUrlBuilder;

    @Value("${app.storage.root-path}")
    private String rootPath;

    public AttachmentServiceImpl(MailAttachmentMapper attachmentMapper,
                                 MailUserBoxMapper mailUserBoxMapper,
                                 PublicUrlBuilder publicUrlBuilder) {
        this.attachmentMapper = attachmentMapper;
        this.mailUserBoxMapper = mailUserBoxMapper;
        this.publicUrlBuilder = publicUrlBuilder;
    }

    @Override
    public AttachmentUploadResponse upload(MultipartFile file) {
        Long ownerUserId = UserContext.requireUserId();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String extension = "";
        int index = originalName.lastIndexOf('.');
        if (index >= 0) {
            extension = originalName.substring(index);
        }

        LocalDate today = LocalDate.now();
        Path targetDir = Paths.get(
                rootPath,
                String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()),
                String.format("%02d", today.getDayOfMonth())
        );
        String storedName = UUID.randomUUID() + extension;
        Path targetFile = targetDir.resolve(storedName);

        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(500, "保存附件失败");
        }

        MailAttachment attachment = new MailAttachment();
        attachment.setOwnerUserId(ownerUserId);
        attachment.setOriginalName(originalName);
        attachment.setStoredName(storedName);
        attachment.setStoragePath(targetFile.toString());
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setTempFlag(1);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);

        return new AttachmentUploadResponse(
                attachment.getId(),
                attachment.getOriginalName(),
                publicUrlBuilder.attachmentDownloadUrl(attachment.getId()),
                attachment.getFileSize(),
                attachment.getContentType()
        );
    }

    @Override
    public Long cloneForMail(Long sourceAttachmentId, Long targetMailId, Long userId) {
        MailAttachment source = getById(sourceAttachmentId);
        assertCanAccess(source);
        Path sourcePath = Paths.get(source.getStoragePath());
        if (!Files.exists(sourcePath)) {
            throw new BusinessException(404, "附件文件不存在");
        }

        String originalName = source.getOriginalName();
        String extension = "";
        int index = originalName.lastIndexOf('.');
        if (index >= 0) {
            extension = originalName.substring(index);
        }

        LocalDate today = LocalDate.now();
        Path targetDir = Paths.get(
                rootPath,
                String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()),
                String.format("%02d", today.getDayOfMonth())
        );
        String storedName = UUID.randomUUID() + extension;
        Path targetFile = targetDir.resolve(storedName);

        try {
            Files.createDirectories(targetDir);
            Files.copy(sourcePath, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new BusinessException(500, "复制附件失败");
        }

        MailAttachment cloned = new MailAttachment();
        cloned.setMailId(targetMailId);
        cloned.setOwnerUserId(userId);
        cloned.setOriginalName(originalName);
        cloned.setStoredName(storedName);
        cloned.setStoragePath(targetFile.toString());
        cloned.setContentType(source.getContentType());
        cloned.setFileSize(source.getFileSize());
        cloned.setFileHash(source.getFileHash());
        cloned.setTempFlag(0);
        cloned.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(cloned);
        return cloned.getId();
    }

    @Override
    public MailAttachment getById(Long id) {
        MailAttachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BusinessException(404, "附件不存在");
        }
        return attachment;
    }

    @Override
    public Resource loadAsResource(Long id) {
        MailAttachment attachment = getById(id);
        assertCanAccess(attachment);
        Path path = Paths.get(attachment.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException(404, "附件文件不存在");
        }
        return new FileSystemResource(path);
    }

    private void assertCanAccess(MailAttachment attachment) {
        Long userId = UserContext.requireUserId();
        if (userId.equals(attachment.getOwnerUserId())) {
            return;
        }
        if (attachment.getMailId() != null) {
            Long count = mailUserBoxMapper.selectCount(new LambdaQueryWrapper<MailUserBox>()
                    .eq(MailUserBox::getMailId, attachment.getMailId())
                    .eq(MailUserBox::getOwnerUserId, userId));
            if (count != null && count > 0) {
                return;
            }
        }
        throw new BusinessException(403, "无权访问该附件");
    }
}

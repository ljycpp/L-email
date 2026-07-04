package com.practice.mailsystem.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.mailsystem.attachment.entity.MailAttachment;
import com.practice.mailsystem.attachment.mapper.MailAttachmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时清理孤儿临时附件（temp_flag=1 且超过 24 小时未关联到邮件）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TempAttachmentCleanupTask {

    private static final int ORPHAN_HOURS = 24;

    private final MailAttachmentMapper attachmentMapper;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanUpOrphanTempAttachments() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ORPHAN_HOURS);
        List<MailAttachment> orphans = attachmentMapper.selectList(new LambdaQueryWrapper<MailAttachment>()
                .eq(MailAttachment::getTempFlag, 1)
                .isNull(MailAttachment::getMailId)
                .lt(MailAttachment::getCreatedAt, cutoff));
        if (orphans.isEmpty()) {
            return;
        }
        int deleted = 0;
        for (MailAttachment att : orphans) {
            try {
                if (att.getStoragePath() != null) {
                    Files.deleteIfExists(Path.of(att.getStoragePath()));
                }
                attachmentMapper.deleteById(att.getId());
                deleted++;
            } catch (Exception ex) {
                log.warn("Failed to clean up temp attachment id={}, path={}",
                        att.getId(), att.getStoragePath(), ex);
            }
        }
        log.info("Temp attachment cleanup: removed {} orphan record(s) older than {} hours",
                deleted, ORPHAN_HOURS);
    }
}

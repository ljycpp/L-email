package com.practice.mailsystem.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.practice.mailsystem.attachment.entity.MailAttachment;
import com.practice.mailsystem.attachment.mapper.MailAttachmentMapper;
import com.practice.mailsystem.attachment.service.AttachmentService;
import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.PageResult;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.mail.dto.LegacyMailFormRequest;
import com.practice.mailsystem.mail.dto.MailCreateRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import com.practice.mailsystem.mail.dto.PartyRequest;
import com.practice.mailsystem.mail.entity.MailLabel;
import com.practice.mailsystem.mail.entity.MailMessage;
import com.practice.mailsystem.mail.entity.MailRecipient;
import com.practice.mailsystem.mail.entity.MailUserBox;
import com.practice.mailsystem.mail.entity.MailUserLabel;
import com.practice.mailsystem.mail.mapper.MailLabelMapper;
import com.practice.mailsystem.mail.mapper.MailMessageMapper;
import com.practice.mailsystem.mail.mapper.MailRecipientMapper;
import com.practice.mailsystem.mail.mapper.MailUserBoxMapper;
import com.practice.mailsystem.mail.mapper.MailUserLabelMapper;
import com.practice.mailsystem.mail.service.MailListQueryService;
import com.practice.mailsystem.mail.service.MailService;
import com.practice.mailsystem.mail.util.MailBoxUtils;
import com.practice.mailsystem.mail.vo.DraftItemVO;
import com.practice.mailsystem.mail.vo.InboxItemVO;
import com.practice.mailsystem.mail.vo.MailDetailVO;
import com.practice.mailsystem.mail.vo.MailListItemVO;
import com.practice.mailsystem.mail.vo.OutboxItemVO;
import com.practice.mailsystem.user.entity.SysUser;
import com.practice.mailsystem.user.mapper.SysUserMapper;
import com.practice.mailsystem.priority.service.MailPriorityAutoService;
import com.practice.mailsystem.spam.service.SpamAutoFilterService;
import com.practice.mailsystem.websocket.MailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MailServiceImpl implements MailService {

    private static final String BOX_INBOX   = "INBOX";
    private static final String BOX_OUTBOX  = "OUTBOX";
    private static final String BOX_DRAFT   = "DRAFT";
    private static final String ROLE_SENDER = "SENDER";
    private static final String ROLE_TO     = "TO";
    private static final String ROLE_CC     = "CC";

    private final MailMessageMapper mailMessageMapper;
    private final MailUserBoxMapper mailUserBoxMapper;
    private final MailRecipientMapper recipientMapper;
    private final MailAttachmentMapper attachmentMapper;
    private final MailLabelMapper labelMapper;
    private final MailUserLabelMapper userLabelMapper;
    private final SysUserMapper userMapper;
    private final MailNotificationService mailNotificationService;
    private final AttachmentService attachmentService;
    private final SpamAutoFilterService spamAutoFilterService;
    private final MailPriorityAutoService mailPriorityAutoService;
    private final MailListQueryService mailListQueryService;
    private final Executor mailTaskExecutor;

    public MailServiceImpl(MailMessageMapper mailMessageMapper,
                           MailUserBoxMapper mailUserBoxMapper,
                           MailRecipientMapper recipientMapper,
                           MailAttachmentMapper attachmentMapper,
                           MailLabelMapper labelMapper,
                           MailUserLabelMapper userLabelMapper,
                           SysUserMapper userMapper,
                           MailNotificationService mailNotificationService,
                           AttachmentService attachmentService,
                           SpamAutoFilterService spamAutoFilterService,
                           MailPriorityAutoService mailPriorityAutoService,
                           MailListQueryService mailListQueryService,
                           @Qualifier("mailTaskExecutor") Executor mailTaskExecutor) {
        this.mailMessageMapper = mailMessageMapper;
        this.mailUserBoxMapper = mailUserBoxMapper;
        this.recipientMapper = recipientMapper;
        this.attachmentMapper = attachmentMapper;
        this.labelMapper = labelMapper;
        this.userLabelMapper = userLabelMapper;
        this.userMapper = userMapper;
        this.mailNotificationService = mailNotificationService;
        this.attachmentService = attachmentService;
        this.spamAutoFilterService = spamAutoFilterService;
        this.mailPriorityAutoService = mailPriorityAutoService;
        this.mailListQueryService = mailListQueryService;
        this.mailTaskExecutor = mailTaskExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long send(MailCreateRequest request) {
        Long userId = UserContext.requireUserId();
        List<PartyRequest> targets = normalizeParties(request.safeTarget());
        List<PartyRequest> copies = normalizePartiesExcluding(request.safeCopy(), targets);
        if (targets.isEmpty()) {
            throw new BusinessException("???????");
        }

        MailMessage message;
        if (request.draftId() != null) {
            message = mailMessageMapper.selectById(request.draftId());
            if (message == null || message.getDraftFlag() == null || message.getDraftFlag() != 1) {
                throw new BusinessException(400, "?????????");
            }
            MailUserBox draftBox = getOwnedDraftBox(message, userId);
            message.setSubject(request.title());
            message.setContentHtml(request.content());
            message.setContentText(stripHtml(request.content()));
            message.setDraftFlag(0);
            message.setHasAttachment(request.safeAttachmentIds().isEmpty() ? 0 : 1);
            message.setSentAt(LocalDateTime.now());
            message.setUpdatedAt(LocalDateTime.now());
            mailMessageMapper.updateById(message);
            recipientMapper.delete(new LambdaQueryWrapper<MailRecipient>().eq(MailRecipient::getMailId, message.getId()));
            syncDraftAttachments(message.getId(), userId, request.safeAttachmentIds());
            userLabelMapper.delete(new LambdaQueryWrapper<MailUserLabel>()
                    .eq(MailUserLabel::getUserBoxId, draftBox.getId()));
            mailUserBoxMapper.deleteById(draftBox.getId());
        } else {
            message = buildMessage(request, userId, 0);
            message.setSentAt(LocalDateTime.now());
            mailMessageMapper.insert(message);
            attachFiles(message.getId(), userId, request.safeAttachmentIds());
        }

        saveRecipients(message.getId(), targets, ROLE_TO);
        saveRecipients(message.getId(), copies, ROLE_CC);
        saveUserBox(message.getId(), userId, BOX_OUTBOX, ROLE_SENDER, 1);
        deliverToRecipients(message, targets, ROLE_TO, userId);
        deliverToRecipients(message, copies, ROLE_CC, userId);
        return message.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sendLegacyForm(LegacyMailFormRequest request) {
        return send(toMailCreateRequest(request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(MailCreateRequest request) {
        Long userId = UserContext.requireUserId();
        List<PartyRequest> targets = normalizeParties(request.safeTarget());
        List<PartyRequest> copies = normalizePartiesExcluding(request.safeCopy(), targets);
        MailMessage message;
        if (request.draftId() != null) {
            message = mailMessageMapper.selectById(request.draftId());
            ensureDraftOwner(message, userId);
            message.setSubject(request.title());
            message.setContentHtml(request.content());
            message.setContentText(stripHtml(request.content()));
            message.setHasAttachment(request.safeAttachmentIds().isEmpty() ? 0 : 1);
            message.setUpdatedAt(LocalDateTime.now());
            mailMessageMapper.updateById(message);
            recipientMapper.delete(new LambdaQueryWrapper<MailRecipient>().eq(MailRecipient::getMailId, message.getId()));
        } else {
            message = buildMessage(request, userId, 1);
            mailMessageMapper.insert(message);
            saveUserBox(message.getId(), userId, BOX_DRAFT, ROLE_SENDER, 1);
        }
        saveRecipients(message.getId(), targets, ROLE_TO);
        saveRecipients(message.getId(), copies, ROLE_CC);
        syncDraftAttachments(message.getId(), userId, request.safeAttachmentIds());
        return message.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveLegacyDraft(LegacyMailFormRequest request) {
        return saveDraft(toMailCreateRequest(request));
    }

    @Override
    public PageResult<InboxItemVO> listInbox(MailListQuery query) {
        return mailListQueryService.listInbox(query);
    }

    @Override
    public PageResult<OutboxItemVO> listOutbox(MailListQuery query) {
        return mailListQueryService.listOutbox(query);
    }

    @Override
    public PageResult<DraftItemVO> listDrafts(MailListQuery query) {
        return mailListQueryService.listDrafts(query);
    }

    @Override
    public PageResult<MailListItemVO> listByMailList(MailListQuery query, Map<String, String> requestParams) {
        return mailListQueryService.listByMailList(query, requestParams);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MailDetailVO getDetail(Long mailId, String mailType) {
        return mailListQueryService.getDetail(mailId, mailType);
    }

    @Override
    public void deleteMail(Long mailId) {
        updateDeleteFlag(mailId, 1);
    }

    @Override
    public void undoDelete(Long mailId) {
        updateDeleteFlag(mailId, 0);
    }

    @Override
    public void deleteMails(Iterable<Long> mailIds) {
        deleteMails(mailIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMails(Iterable<Long> mailIds, String boxType) {
        for (Long mailId : mailIds) {
            if (mailId != null) {
                updateDeleteFlag(mailId, normalizeBoxType(boxType), 1);
            }
        }
    }

    @Override
    public void undoDeleteMails(Iterable<Long> mailIds) {
        undoDeleteMails(mailIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void undoDeleteMails(Iterable<Long> mailIds, String boxType) {
        for (Long mailId : mailIds) {
            if (mailId != null) {
                updateDeleteFlag(mailId, normalizeBoxType(boxType), 0);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermanently(Iterable<Long> mailIds) {
        deletePermanently(mailIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermanently(Iterable<Long> mailIds, String boxType) {
        Long userId = UserContext.requireUserId();
        String normalizedBoxType = normalizeBoxType(boxType);
        for (Long mailId : mailIds) {
            if (mailId == null) {
                continue;
            }
            MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                    .eq(MailUserBox::getMailId, mailId)
                    .eq(MailUserBox::getOwnerUserId, userId)
                    .eq(MailUserBox::getDeletedFlag, 1)
                    .eq(StringUtils.hasText(normalizedBoxType), MailUserBox::getBoxType, normalizedBoxType)
                    .last("limit 1"));
            if (box == null) {
                throw new BusinessException(400, "?????????????");
            }
            userLabelMapper.delete(new LambdaQueryWrapper<MailUserLabel>()
                    .eq(MailUserLabel::getUserBoxId, box.getId()));
            mailUserBoxMapper.deleteById(box.getId());

            Long refCount = mailUserBoxMapper.selectCount(new LambdaQueryWrapper<MailUserBox>()
                    .eq(MailUserBox::getMailId, mailId));
            if (refCount == null || refCount == 0) {
                List<MailAttachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<MailAttachment>()
                        .eq(MailAttachment::getMailId, mailId));
                recipientMapper.delete(new LambdaQueryWrapper<MailRecipient>()
                        .eq(MailRecipient::getMailId, mailId));
                attachmentMapper.delete(new LambdaQueryWrapper<MailAttachment>()
                        .eq(MailAttachment::getMailId, mailId));
                scheduleAttachmentFileDeletion(attachments);
                mailMessageMapper.deleteById(mailId);
            }
        }
    }

    @Override
    public void markRead(Long mailId) {
        MailUserBox box = findBoxByMailId(mailId, BOX_INBOX);
        box.setReadFlag(1);
        box.setReadAt(LocalDateTime.now());
        box.setUpdatedAt(LocalDateTime.now());
        mailUserBoxMapper.updateById(box);
    }

    @Override
    public void toggleStar(Long mailId) {
        toggleStar(mailId, null);
    }

    private void toggleStar(Long mailId, String boxType) {
        MailUserBox box = findBoxByMailId(mailId, boxType);
        box.setStarFlag(box.getStarFlag() != null && box.getStarFlag() == 1 ? 0 : 1);
        box.setUpdatedAt(LocalDateTime.now());
        mailUserBoxMapper.updateById(box);
    }

    @Override
    public void toggleStars(Iterable<Long> mailIds) {
        toggleStars(mailIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStars(Iterable<Long> mailIds, String boxType) {
        String normalizedBoxType = normalizeBoxType(boxType);
        for (Long mailId : mailIds) {
            if (mailId != null) {
                toggleStar(mailId, normalizedBoxType);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllInboxRead() {
        Long userId = UserContext.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        MailUserBox update = new MailUserBox();
        update.setReadFlag(1);
        update.setReadAt(now);
        update.setUpdatedAt(now);
        mailUserBoxMapper.update(update, new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getBoxType, BOX_INBOX)
                .eq(MailUserBox::getDeletedFlag, 0)
                .eq(MailUserBox::getSpamFlag, 0)
                .eq(MailUserBox::getReadFlag, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsSpam(Iterable<Long> mailIds) {
        for (Long mailId : mailIds) {
            if (mailId == null) {
                continue;
            }
            MailUserBox box = findBoxByMailId(mailId, BOX_INBOX);
            box.setSpamFlag(1);
            box.setUpdatedAt(LocalDateTime.now());
            mailUserBoxMapper.updateById(box);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unmarkSpam(Iterable<Long> mailIds) {
        Long userId = UserContext.requireUserId();
        for (Long mailId : mailIds) {
            if (mailId == null) {
                continue;
            }
            MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                    .eq(MailUserBox::getMailId, mailId)
                    .eq(MailUserBox::getOwnerUserId, userId)
                    .eq(MailUserBox::getBoxType, BOX_INBOX)
                    .eq(MailUserBox::getSpamFlag, 1)
                    .last("limit 1"));
            if (box == null) {
                continue;
            }
            box.setSpamFlag(0);
            box.setSpamReason(null);
            box.setSpamScore(null);
            box.setSpamDetectedAt(null);
            box.setUpdatedAt(LocalDateTime.now());
            mailUserBoxMapper.updateById(box);
        }
    }

    @Override
    public long countUnreadInbox() {
        return mailListQueryService.countUnreadInbox();
    }

    @Override
    public long countSpam() {
        return mailListQueryService.countSpam();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markLabel(Long labelId, Iterable<Long> mailIds) {
        markLabel(labelId, mailIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markLabel(Long labelId, Iterable<Long> mailIds, String boxType) {
        Long userId = UserContext.requireUserId();
        String normalizedBoxType = normalizeBoxType(boxType);
        MailLabel label = labelMapper.selectOne(new LambdaQueryWrapper<MailLabel>()
                .eq(MailLabel::getId, labelId)
                .eq(MailLabel::getUserId, userId)
                .last("limit 1"));
        if (label == null) {
            throw new BusinessException(404, "?????");
        }
        for (Long mailId : mailIds) {
            if (mailId == null) {
                continue;
            }
            MailUserBox box = findBoxByMailId(mailId, normalizedBoxType);
            Long count = userLabelMapper.selectCount(new LambdaQueryWrapper<MailUserLabel>()
                    .eq(MailUserLabel::getUserBoxId, box.getId())
                    .eq(MailUserLabel::getLabelId, labelId));
            if (count == null || count == 0) {
                MailUserLabel relation = new MailUserLabel();
                relation.setUserBoxId(box.getId());
                relation.setLabelId(labelId);
                relation.setCreatedAt(LocalDateTime.now());
                userLabelMapper.insert(relation);
            }
        }
    }

    private List<PartyRequest> normalizeParties(List<PartyRequest> parties) {
        if (parties == null || parties.isEmpty()) {
            return List.of();
        }
        Map<String, PartyRequest> byEmail = new LinkedHashMap<>();
        for (PartyRequest party : parties) {
            if (party == null || !StringUtils.hasText(party.mail())) {
                continue;
            }
            String email = party.mail().trim().toLowerCase();
            String name = StringUtils.hasText(party.name()) ? party.name().trim() : email;
            byEmail.putIfAbsent(email, new PartyRequest(name, email));
        }
        return new ArrayList<>(byEmail.values());
    }

    private List<PartyRequest> normalizePartiesExcluding(List<PartyRequest> parties, List<PartyRequest> excluded) {
        Set<String> excludedEmails = excluded == null ? Set.of() : excluded.stream()
                .map(PartyRequest::mail)
                .filter(StringUtils::hasText)
                .map(mail -> mail.trim().toLowerCase())
                .collect(Collectors.toSet());
        return normalizeParties(parties).stream()
                .filter(party -> !excludedEmails.contains(party.mail()))
                .toList();
    }

    private MailMessage buildMessage(MailCreateRequest request, Long userId, int draftFlag) {
        MailMessage message = new MailMessage();
        message.setSubject(request.title());
        message.setContentHtml(request.content());
        message.setContentText(stripHtml(request.content()));
        message.setSenderUserId(userId);
        message.setDraftFlag(draftFlag);
        message.setHasAttachment(request.safeAttachmentIds().isEmpty() ? 0 : 1);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        return message;
    }

    private MailCreateRequest toMailCreateRequest(LegacyMailFormRequest request) {
        List<PartyRequest> targets = request.safeTargets().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(mail -> new PartyRequest(mail, mail))
                .toList();
        List<PartyRequest> copies = request.safeCopies().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(mail -> new PartyRequest(mail, mail))
                .toList();
        String title = StringUtils.hasText(request.title()) ? request.title() : "(No Subject)";
        String content = request.content() == null ? "" : request.content();
        return new MailCreateRequest(request.draftId(), title, content, targets, copies, request.safeAttachmentIds());
    }

    private void saveRecipients(Long mailId, List<PartyRequest> recipients, String type) {
        for (PartyRequest item : recipients) {
            MailRecipient recipient = new MailRecipient();
            recipient.setMailId(mailId);
            recipient.setRecipientUserId(findUserIdByEmail(item.mail()));
            recipient.setRecipientName(item.name());
            recipient.setRecipientEmail(item.mail());
            recipient.setRecipientType(type);
            recipient.setCreatedAt(LocalDateTime.now());
            recipientMapper.insert(recipient);
        }
    }

    private void deliverToRecipients(MailMessage message, List<PartyRequest> recipients, String roleType, Long senderUserId) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        SysUser sender = userMapper.selectById(senderUserId);
        String senderName = sender == null ? "?????" : sender.getNickname();
        String senderMail = sender == null ? "" : sender.getEmail();
        String title = StringUtils.hasText(message.getSubject()) ? message.getSubject() : "?????";

        for (PartyRequest item : recipients) {
            Long targetUserId = findUserIdByEmail(item.mail());
            if (targetUserId == null) {
                continue;
            }
            MailUserBox inboxBox = saveUserBox(message.getId(), targetUserId, BOX_INBOX, roleType, 0);
            schedulePostDeliveryProcessing(targetUserId, inboxBox.getId(), message.getId(), title, senderName, senderMail);
        }
    }

    private void schedulePostDeliveryProcessing(Long targetUserId,
                                                Long inboxBoxId,
                                                Long mailId,
                                                String title,
                                                String senderName,
                                                String senderMail) {
        Runnable task = () -> processPostDelivery(targetUserId, inboxBoxId, mailId, title, senderName, senderMail);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runAsync(task);
                }
            });
        } else {
            runAsync(task);
        }
    }

    private void runAsync(Runnable task) {
        CompletableFuture.runAsync(task, mailTaskExecutor)
                .exceptionally(ex -> {
                    log.warn("Post delivery processing failed", ex);
                    return null;
                });
    }

    private void processPostDelivery(Long targetUserId,
                                     Long inboxBoxId,
                                     Long mailId,
                                     String title,
                                     String senderName,
                                     String senderMail) {
        MailUserBox inboxBox = mailUserBoxMapper.selectById(inboxBoxId);
        MailMessage message = mailMessageMapper.selectById(mailId);
        if (inboxBox == null || message == null) {
            return;
        }
        boolean filtered = false;
        try {
            filtered = spamAutoFilterService.tryAutoFilter(
                    targetUserId, inboxBox, message, senderName, senderMail);
        } catch (Exception ex) {
            log.warn("Spam auto filter failed for userBox {}", inboxBoxId, ex);
        }
        if (filtered) {
            return;
        }
        try {
            mailPriorityAutoService.tryAutoPriority(
                    targetUserId, inboxBox, message, senderName, senderMail);
        } catch (Exception ex) {
            log.warn("Mail priority auto scoring failed for userBox {}", inboxBoxId, ex);
        }
        MailUserBox latestBox = mailUserBoxMapper.selectById(inboxBoxId);
        if (latestBox == null) {
            latestBox = inboxBox;
        }
        try {
            mailNotificationService.notifyNewMail(
                    targetUserId,
                    message.getId(),
                    title,
                    senderName,
                    senderMail,
                    latestBox.getPriorityLevel(),
                    latestBox.getPriorityScore()
            );
        } catch (Exception ex) {
            log.warn("New mail notification failed for userBox {}", inboxBoxId, ex);
        }
    }

    private MailUserBox saveUserBox(Long mailId, Long userId, String boxType, String roleType, int readFlag) {
        MailUserBox box = new MailUserBox();
        box.setMailId(mailId);
        box.setOwnerUserId(userId);
        box.setBoxType(boxType);
        box.setRoleType(roleType);
        box.setReadFlag(readFlag);
        box.setReadAt(readFlag == 1 ? LocalDateTime.now() : null);
        box.setStarFlag(0);
        box.setDeletedFlag(0);
        box.setImportantFlag(0);
        box.setSpamFlag(0);
        box.setPriorityScore(0D);
        box.setCreatedAt(LocalDateTime.now());
        box.setUpdatedAt(LocalDateTime.now());
        mailUserBoxMapper.insert(box);
        return box;
    }

    private void syncDraftAttachments(Long mailId, Long userId, List<Long> attachmentIds) {
        Set<Long> retainedIds = attachmentIds == null ? Set.of() : attachmentIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        List<MailAttachment> existing = attachmentMapper.selectList(new LambdaQueryWrapper<MailAttachment>()
                .eq(MailAttachment::getMailId, mailId)
                .eq(MailAttachment::getOwnerUserId, userId));
        for (MailAttachment attachment : existing) {
            if (!retainedIds.contains(attachment.getId())) {
                attachmentMapper.update(null, new LambdaUpdateWrapper<MailAttachment>()
                        .eq(MailAttachment::getId, attachment.getId())
                        .set(MailAttachment::getMailId, null)
                        .set(MailAttachment::getTempFlag, 1));
            }
        }
        attachFiles(mailId, userId, attachmentIds == null ? List.of() : attachmentIds);
    }

    private void scheduleAttachmentFileDeletion(List<MailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteAttachmentFiles(attachments);
                }
            });
        } else {
            deleteAttachmentFiles(attachments);
        }
    }

    private void deleteAttachmentFiles(List<MailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        for (MailAttachment attachment : attachments) {
            if (attachment == null || !StringUtils.hasText(attachment.getStoragePath())) {
                continue;
            }
            try {
                Files.deleteIfExists(Path.of(attachment.getStoragePath()));
            } catch (Exception ex) {
                log.warn("Failed to delete attachment file {} for attachment {}",
                        attachment.getStoragePath(), attachment.getId(), ex);
            }
        }
    }

    private void attachFiles(Long mailId, Long userId, List<Long> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return;
        }
        for (Long attachmentId : attachmentIds) {
            if (attachmentId == null) {
                continue;
            }
            MailAttachment attachment = attachmentMapper.selectById(attachmentId);
            if (attachment == null) {
                continue;
            }
            if (!canUseAttachment(attachment, userId)) {
                throw new BusinessException(403, "???????");
            }
            if (attachment.getMailId() != null && !attachment.getMailId().equals(mailId)) {
                attachmentService.cloneForMail(attachmentId, mailId, userId);
                continue;
            }
            attachment.setMailId(mailId);
            attachment.setTempFlag(0);
            attachmentMapper.updateById(attachment);
        }
    }

    private boolean canUseAttachment(MailAttachment attachment, Long userId) {
        if (userId.equals(attachment.getOwnerUserId())) {
            return true;
        }
        if (attachment.getMailId() == null) {
            return false;
        }
        Long count = mailUserBoxMapper.selectCount(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getMailId, attachment.getMailId())
                .eq(MailUserBox::getOwnerUserId, userId));
        return count != null && count > 0;
    }

    private MailUserBox findBoxByMailId(Long mailId) {
        return findBoxByMailId(mailId, null);
    }

    private MailUserBox findBoxByMailId(Long mailId, String boxType) {
        Long userId = UserContext.requireUserId();
        String normalizedBoxType = normalizeBoxType(boxType);
        MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getMailId, mailId)
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(StringUtils.hasText(normalizedBoxType), MailUserBox::getBoxType, normalizedBoxType)
                .last("limit 1"));
        if (box == null) {
            throw new BusinessException(404, "?????");
        }
        return box;
    }

    private void updateDeleteFlag(Long mailId, int deletedFlag) {
        updateDeleteFlag(mailId, null, deletedFlag);
    }

    private void updateDeleteFlag(Long mailId, String boxType, int deletedFlag) {
        MailUserBox box = findBoxByMailId(mailId, boxType);
        box.setDeletedFlag(deletedFlag);
        box.setUpdatedAt(LocalDateTime.now());
        mailUserBoxMapper.updateById(box);
    }

    private void ensureDraftOwner(MailMessage message, Long userId) {
        getOwnedDraftBox(message, userId);
    }

    private MailUserBox getOwnedDraftBox(MailMessage message, Long userId) {
        if (message == null) {
            throw new BusinessException(404, "?????");
        }
        MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getMailId, message.getId())
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getBoxType, BOX_DRAFT)
                .last("limit 1"));
        if (box == null) {
            throw new BusinessException(403, "???????");
        }
        return box;
    }

    private Long findUserIdByEmail(String email) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)
                .last("limit 1"));
        return user == null ? null : user.getId();
    }

    private String normalizeBoxType(String boxType) {
        return MailBoxUtils.normalizeBoxType(boxType);
    }

    private String stripHtml(String html) {
        return MailBoxUtils.stripHtml(html);
    }
}

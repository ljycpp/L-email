package com.practice.mailsystem.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.practice.mailsystem.mail.service.MailService;
import com.practice.mailsystem.mail.vo.AttachmentItemVO;
import com.practice.mailsystem.mail.vo.DraftItemVO;
import com.practice.mailsystem.mail.vo.InboxItemVO;
import com.practice.mailsystem.mail.vo.LabelItemVO;
import com.practice.mailsystem.mail.vo.MailDetailVO;
import com.practice.mailsystem.mail.vo.MailListItemVO;
import com.practice.mailsystem.mail.vo.OutboxItemVO;
import com.practice.mailsystem.mail.vo.PartyVO;
import com.practice.mailsystem.common.PublicUrlBuilder;
import com.practice.mailsystem.user.entity.SysUser;
import com.practice.mailsystem.user.mapper.SysUserMapper;
import com.practice.mailsystem.priority.service.MailPriorityAutoService;
import com.practice.mailsystem.spam.service.SpamAutoFilterService;
import com.practice.mailsystem.websocket.MailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private static final String BOX_INBOX = "INBOX";
    private static final String BOX_OUTBOX = "OUTBOX";
    private static final String BOX_DRAFT = "DRAFT";
    private static final String ROLE_SENDER = "SENDER";
    private static final String ROLE_TO = "TO";
    private static final String ROLE_CC = "CC";

    private final MailMessageMapper mailMessageMapper;
    private final MailUserBoxMapper mailUserBoxMapper;
    private final MailRecipientMapper recipientMapper;
    private final MailAttachmentMapper attachmentMapper;
    private final MailLabelMapper labelMapper;
    private final MailUserLabelMapper userLabelMapper;
    private final SysUserMapper userMapper;
    private final PublicUrlBuilder publicUrlBuilder;
    private final MailNotificationService mailNotificationService;
    private final AttachmentService attachmentService;
    private final SpamAutoFilterService spamAutoFilterService;
    private final MailPriorityAutoService mailPriorityAutoService;

    public MailServiceImpl(MailMessageMapper mailMessageMapper,
                           MailUserBoxMapper mailUserBoxMapper,
                           MailRecipientMapper recipientMapper,
                           MailAttachmentMapper attachmentMapper,
                           MailLabelMapper labelMapper,
                           MailUserLabelMapper userLabelMapper,
                           SysUserMapper userMapper,
                           PublicUrlBuilder publicUrlBuilder,
                           MailNotificationService mailNotificationService,
                           AttachmentService attachmentService,
                           SpamAutoFilterService spamAutoFilterService,
                           MailPriorityAutoService mailPriorityAutoService) {
        this.mailMessageMapper = mailMessageMapper;
        this.mailUserBoxMapper = mailUserBoxMapper;
        this.recipientMapper = recipientMapper;
        this.attachmentMapper = attachmentMapper;
        this.labelMapper = labelMapper;
        this.userLabelMapper = userLabelMapper;
        this.userMapper = userMapper;
        this.publicUrlBuilder = publicUrlBuilder;
        this.mailNotificationService = mailNotificationService;
        this.attachmentService = attachmentService;
        this.spamAutoFilterService = spamAutoFilterService;
        this.mailPriorityAutoService = mailPriorityAutoService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long send(MailCreateRequest request) {
        Long userId = requiredUserId();
        List<PartyRequest> targets = normalizeParties(request.safeTarget());
        List<PartyRequest> copies = normalizePartiesExcluding(request.safeCopy(), targets);
        if (targets.isEmpty()) {
            throw new BusinessException("收件人不能为空");
        }

        MailMessage message;
        if (request.draftId() != null) {
            message = mailMessageMapper.selectById(request.draftId());
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
        Long userId = requiredUserId();
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
        Page<MailUserBox> page = queryBox(query, BOX_INBOX, false);
        if (page.getRecords().isEmpty()) return new PageResult<>(page.getTotal(), List.of());
        List<Long> mailIds = page.getRecords().stream().map(MailUserBox::getMailId).distinct().toList();
        Map<Long, MailMessage> msgMap = batchLoadMessages(mailIds);
        Map<Long, SysUser> userMap = batchLoadSenders(msgMap.values());
        Map<Long, List<LabelItemVO>> labelMap = batchLoadLabels(
                page.getRecords().stream().map(MailUserBox::getId).toList());
        List<InboxItemVO> items = page.getRecords().stream().map(box -> {
            MailMessage msg = msgMap.get(box.getMailId());
            SysUser sender = msg != null ? userMap.get(msg.getSenderUserId()) : null;
            return new InboxItemVO(
                    box.getMailId(),
                    box.getStarFlag() == 1,
                    msg != null && msg.getHasAttachment() != null && msg.getHasAttachment() == 1,
                    false,
                    box.getReadFlag() == 1 ? 1 : 0,
                    sender == null ? "未知用户" : sender.getNickname(),
                    sender == null ? "" : sender.getEmail(),
                    labelMap.getOrDefault(box.getId(), List.of()),
                    msg == null ? "" : msg.getSubject(),
                    buildPreview(msg),
                    box.getCreatedAt(),
                    box.getReadAt(),
                    box.getSpamReason(),
                    box.getSpamScore(),
                    box.getPriorityLevel(),
                    box.getPriorityScore(),
                    box.getPriorityReason());
        }).toList();
        return new PageResult<>(page.getTotal(), items);
    }

    @Override
    public PageResult<OutboxItemVO> listOutbox(MailListQuery query) {
        Page<MailUserBox> page = queryBox(query, BOX_OUTBOX, false);
        if (page.getRecords().isEmpty()) return new PageResult<>(page.getTotal(), List.of());
        List<Long> mailIds = page.getRecords().stream().map(MailUserBox::getMailId).distinct().toList();
        Map<Long, MailMessage> msgMap = batchLoadMessages(mailIds);
        Map<Long, List<PartyVO>> recipientMap = batchLoadRecipients(mailIds, ROLE_TO, ROLE_CC);
        Map<Long, List<LabelItemVO>> labelMap = batchLoadLabels(
                page.getRecords().stream().map(MailUserBox::getId).toList());
        List<OutboxItemVO> items = page.getRecords().stream()
                .map(box -> {
                    MailMessage msg = msgMap.get(box.getMailId());
                    return new OutboxItemVO(
                            box.getMailId(),
                            box.getStarFlag() == 1,
                            msg != null && msg.getHasAttachment() != null && msg.getHasAttachment() == 1,
                            false,
                            recipientMap.getOrDefault(box.getMailId(), List.of()),
                            labelMap.getOrDefault(box.getId(), List.of()),
                            msg == null ? "" : msg.getSubject(),
                            msg == null ? box.getCreatedAt() : msg.getSentAt());
                }).toList();
        return new PageResult<>(page.getTotal(), items);
    }

    @Override
    public PageResult<DraftItemVO> listDrafts(MailListQuery query) {
        Page<MailUserBox> page = queryBox(query, BOX_DRAFT, false);
        if (page.getRecords().isEmpty()) return new PageResult<>(page.getTotal(), List.of());
        List<Long> mailIds = page.getRecords().stream().map(MailUserBox::getMailId).distinct().toList();
        Map<Long, MailMessage> msgMap = batchLoadMessages(mailIds);
        Map<Long, List<PartyVO>> recipientMap = batchLoadRecipients(mailIds, ROLE_TO, ROLE_CC);
        Map<Long, List<LabelItemVO>> labelMap = batchLoadLabels(
                page.getRecords().stream().map(MailUserBox::getId).toList());
        List<DraftItemVO> items = page.getRecords().stream()
                .map(box -> {
            MailMessage msg = msgMap.get(box.getMailId());
            return new DraftItemVO(
                    box.getMailId(),
                    box.getStarFlag() == 1,
                    msg != null && msg.getHasAttachment() != null && msg.getHasAttachment() == 1,
                    false,
                    recipientMap.getOrDefault(box.getMailId(), List.of()),
                    labelMap.getOrDefault(box.getId(), List.of()),
                    msg == null ? "" : msg.getSubject(),
                    msg == null ? box.getCreatedAt() : msg.getCreatedAt(),
                    msg == null ? box.getUpdatedAt() : msg.getUpdatedAt());
        }).toList();
        return new PageResult<>(page.getTotal(), items);
    }

    @Override
    public PageResult<MailListItemVO> listByMailList(MailListQuery query, Map<String, String> requestParams) {
        boolean deleted = "true".equalsIgnoreCase(requestParams.get("routeQuery[isDeleted]"))
                || "true".equalsIgnoreCase(requestParams.get("isDeleted"));
        if (!deleted) {
            String labelIdStr = requestParams.getOrDefault("routeQuery[labelId]", requestParams.get("labelId"));
            if (query.getLabelId() == null && StringUtils.hasText(labelIdStr)) {
                query.setLabelId(Long.valueOf(labelIdStr));
            }
            return listByLabel(query);
        }

        Long userId = requiredUserId();
        LambdaQueryWrapper<MailUserBox> wrapper = new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getDeletedFlag, 1)
                .orderByDesc(MailUserBox::getUpdatedAt);
        applyTrashListFilters(wrapper, query, userId);
        Page<MailUserBox> page = mailUserBoxMapper.selectPage(new Page<>(query.getPage(), query.getLimit()), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
        List<MailListItemVO> items = page.getRecords().stream().map(this::toMailListItem).toList();
        return new PageResult<>(page.getTotal(), items);
    }

    /** 按标签查看邮件（未删除） */
    private PageResult<MailListItemVO> listByLabel(MailListQuery query) {
        Long userId = requiredUserId();
        LambdaQueryWrapper<MailUserBox> wrapper = new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getDeletedFlag, 0)
                .orderByDesc(MailUserBox::getUpdatedAt);
        applyLabelFilter(wrapper, query.getLabelId(), userId);
        applyTrashListFilters(wrapper, query, userId);
        Page<MailUserBox> page = mailUserBoxMapper.selectPage(new Page<>(query.getPage(), query.getLimit()), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
        List<MailListItemVO> items = page.getRecords().stream().map(this::toMailListItem).toList();
        return new PageResult<>(page.getTotal(), items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MailDetailVO getDetail(Long mailId, String mailType) {
        Long userId = requiredUserId();
        String boxType = normalizeBoxType(mailType);
        MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getMailId, mailId)
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(StringUtils.hasText(boxType), MailUserBox::getBoxType, boxType)
                .last("limit 1"));
        if (box == null) {
            throw new BusinessException(404, "邮件不存在");
        }
        if (BOX_INBOX.equals(boxType) && box.getReadFlag() == 0) {
            box.setReadFlag(1);
            box.setReadAt(LocalDateTime.now());
            box.setUpdatedAt(LocalDateTime.now());
            mailUserBoxMapper.updateById(box);
        }

        MailMessage message = mailMessageMapper.selectById(mailId);
        if (message == null) {
            throw new BusinessException(404, "邮件不存在");
        }
        SysUser sender = userMapper.selectById(message.getSenderUserId());
        List<PartyVO> target = listRecipients(mailId, ROLE_TO);
        List<PartyVO> copy = listRecipients(mailId, ROLE_CC);
        List<AttachmentItemVO> attachments = listAttachments(mailId);
        List<LabelItemVO> labels = listLabels(box.getId());
        return new MailDetailVO(
                message.getId(),
                message.getSubject(),
                message.getContentHtml(),
                sender == null ? "未知用户" : sender.getNickname(),
                sender == null ? "" : sender.getEmail(),
                box.getCreatedAt(),
                message.getSentAt(),
                box.getStarFlag() == 1,
                target,
                copy,
                attachments,
                List.of(),
                labels,
                box.getSpamReason(),
                box.getSpamScore(),
                box.getSpamDetectedAt() != null,
                box.getPriorityLevel(),
                box.getPriorityScore(),
                box.getPriorityReason(),
                box.getPriorityScoredAt() != null
        );
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
        Long userId = requiredUserId();
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
                throw new BusinessException(400, "仅可彻底删除回收站中的邮件");
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
        Long userId = requiredUserId();
        List<MailUserBox> boxes = mailUserBoxMapper.selectList(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getBoxType, BOX_INBOX)
                .eq(MailUserBox::getDeletedFlag, 0)
                .eq(MailUserBox::getSpamFlag, 0)
                .eq(MailUserBox::getReadFlag, 0));
        LocalDateTime now = LocalDateTime.now();
        for (MailUserBox box : boxes) {
            box.setReadFlag(1);
            box.setReadAt(now);
            box.setUpdatedAt(now);
            mailUserBoxMapper.updateById(box);
        }
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
        Long userId = requiredUserId();
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
        Long userId = requiredUserId();
        Long count = mailUserBoxMapper.selectCount(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getBoxType, BOX_INBOX)
                .eq(MailUserBox::getDeletedFlag, 0)
                .eq(MailUserBox::getSpamFlag, 0)
                .eq(MailUserBox::getReadFlag, 0));
        return count == null ? 0L : count;
    }

    @Override
    public long countSpam() {
        Long userId = requiredUserId();
        Long count = mailUserBoxMapper.selectCount(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getBoxType, BOX_INBOX)
                .eq(MailUserBox::getDeletedFlag, 0)
                .eq(MailUserBox::getSpamFlag, 1));
        return count == null ? 0L : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markLabel(Long labelId, Iterable<Long> mailIds) {
        markLabel(labelId, mailIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markLabel(Long labelId, Iterable<Long> mailIds, String boxType) {
        Long userId = requiredUserId();
        String normalizedBoxType = normalizeBoxType(boxType);
        MailLabel label = labelMapper.selectOne(new LambdaQueryWrapper<MailLabel>()
                .eq(MailLabel::getId, labelId)
                .eq(MailLabel::getUserId, userId)
                .last("limit 1"));
        if (label == null) {
            throw new BusinessException(404, "标签不存在");
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
        String senderName = sender == null ? "未知发件人" : sender.getNickname();
        String senderMail = sender == null ? "" : sender.getEmail();
        String title = StringUtils.hasText(message.getSubject()) ? message.getSubject() : "（无主题）";

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
        CompletableFuture.runAsync(task)
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
                throw new BusinessException(403, "无权使用该附件");
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

    private Page<MailUserBox> queryBox(MailListQuery query, String boxType, boolean deleted) {
        Long userId = requiredUserId();
        LambdaQueryWrapper<MailUserBox> wrapper = new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(boxType != null, MailUserBox::getBoxType, boxType)
                .eq(MailUserBox::getDeletedFlag, deleted ? 1 : 0);
        if (BOX_INBOX.equals(boxType) && (query.getSpam() == null || query.getSpam() != 1)) {
            wrapper.orderByDesc(MailUserBox::getPriorityScore).orderByDesc(MailUserBox::getUpdatedAt);
        } else {
            wrapper.orderByDesc(MailUserBox::getUpdatedAt);
        }
        if (BOX_INBOX.equals(boxType)) {
            if (query.getSpam() != null && query.getSpam() == 1) {
                wrapper.eq(MailUserBox::getSpamFlag, 1);
            } else {
                wrapper.eq(MailUserBox::getSpamFlag, 0);
            }
            if (query.getStarred() != null && query.getStarred() == 1) {
                wrapper.eq(MailUserBox::getStarFlag, 1);
            }
            if (query.getStatus() != null) {
                if (query.getStatus() == 0) {
                    wrapper.eq(MailUserBox::getReadFlag, 0);
                } else if (query.getStatus() == 1) {
                    wrapper.eq(MailUserBox::getReadFlag, 1);
                }
            }
        }
        applyKeywordFilter(wrapper, query.getTitle());
        applyLabelFilter(wrapper, query.getLabelId(), userId);
        if (BOX_INBOX.equals(boxType)) {
            applyInboxSenderFilter(wrapper, query);
        } else if (BOX_OUTBOX.equals(boxType) || BOX_DRAFT.equals(boxType)) {
            applyRecipientFilter(wrapper, query);
        }
        return mailUserBoxMapper.selectPage(new Page<>(query.getPage(), query.getLimit()), wrapper);
    }

    private void applyKeywordFilter(LambdaQueryWrapper<MailUserBox> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        List<Long> matchingMailIds = mailMessageMapper.selectList(
                new LambdaQueryWrapper<MailMessage>()
                        .select(MailMessage::getId)
                        .and(q -> q.like(MailMessage::getSubject, keyword)
                                .or()
                                .like(MailMessage::getContentText, keyword)))
                .stream().map(MailMessage::getId).toList();
        if (matchingMailIds.isEmpty()) {
            wrapper.eq(MailUserBox::getMailId, -1L);
            return;
        }
        wrapper.in(MailUserBox::getMailId, matchingMailIds);
    }

    private void applyLabelFilter(LambdaQueryWrapper<MailUserBox> wrapper, Long labelId, Long userId) {
        if (labelId == null) {
            return;
        }
        MailLabel label = labelMapper.selectOne(new LambdaQueryWrapper<MailLabel>()
                .eq(MailLabel::getId, labelId)
                .eq(MailLabel::getUserId, userId)
                .last("limit 1"));
        if (label == null) {
            wrapper.eq(MailUserBox::getId, -1L);
            return;
        }
        List<Long> boxIds = userLabelMapper.selectList(new LambdaQueryWrapper<MailUserLabel>()
                        .eq(MailUserLabel::getLabelId, labelId))
                .stream().map(MailUserLabel::getUserBoxId).distinct().toList();
        if (boxIds.isEmpty()) {
            wrapper.eq(MailUserBox::getId, -1L);
            return;
        }
        wrapper.in(MailUserBox::getId, boxIds);
    }

    private void applyInboxSenderFilter(LambdaQueryWrapper<MailUserBox> wrapper, MailListQuery query) {
        String senderMail = StringUtils.hasText(query.getReceiveMail()) ? query.getReceiveMail() : query.getMail();
        String senderName = StringUtils.hasText(query.getReceiveName()) ? query.getReceiveName() : query.getName();
        if (!StringUtils.hasText(senderMail) && !StringUtils.hasText(senderName)) {
            return;
        }
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(senderMail)) {
            userWrapper.like(SysUser::getEmail, senderMail);
        }
        if (StringUtils.hasText(senderName)) {
            userWrapper.like(SysUser::getNickname, senderName);
        }
        List<Long> senderIds = userMapper.selectList(userWrapper).stream().map(SysUser::getId).toList();
        if (senderIds.isEmpty()) {
            wrapper.eq(MailUserBox::getMailId, -1L);
            return;
        }
        List<Long> mailIds = mailMessageMapper.selectList(
                new LambdaQueryWrapper<MailMessage>()
                        .select(MailMessage::getId)
                        .in(MailMessage::getSenderUserId, senderIds))
                .stream().map(MailMessage::getId).toList();
        if (mailIds.isEmpty()) {
            wrapper.eq(MailUserBox::getMailId, -1L);
            return;
        }
        wrapper.in(MailUserBox::getMailId, mailIds);
    }

    private void applyRecipientFilter(LambdaQueryWrapper<MailUserBox> wrapper, MailListQuery query) {
        String recipientMail = StringUtils.hasText(query.getReceiveMail()) ? query.getReceiveMail() : query.getMail();
        String recipientName = StringUtils.hasText(query.getReceiveName()) ? query.getReceiveName() : query.getName();
        if (!StringUtils.hasText(recipientMail) && !StringUtils.hasText(recipientName)) {
            return;
        }
        LambdaQueryWrapper<MailRecipient> recipientWrapper = new LambdaQueryWrapper<MailRecipient>()
                .select(MailRecipient::getMailId)
                .in(MailRecipient::getRecipientType, List.of(ROLE_TO, ROLE_CC));
        if (StringUtils.hasText(recipientMail)) {
            recipientWrapper.like(MailRecipient::getRecipientEmail, recipientMail);
        }
        if (StringUtils.hasText(recipientName)) {
            recipientWrapper.like(MailRecipient::getRecipientName, recipientName);
        }
        List<Long> mailIds = recipientMapper.selectList(recipientWrapper)
                .stream().map(MailRecipient::getMailId).distinct().toList();
        if (mailIds.isEmpty()) {
            wrapper.eq(MailUserBox::getMailId, -1L);
            return;
        }
        wrapper.in(MailUserBox::getMailId, mailIds);
    }

    private void applyTrashListFilters(LambdaQueryWrapper<MailUserBox> wrapper, MailListQuery query, Long userId) {
        applyKeywordFilter(wrapper, query.getTitle());
        applyLabelFilter(wrapper, query.getLabelId(), userId);
        if (query.getStatus() != null) {
            if (query.getStatus() == 0) {
                wrapper.eq(MailUserBox::getReadFlag, 0);
            } else if (query.getStatus() == 1) {
                wrapper.eq(MailUserBox::getReadFlag, 1);
            }
        }
        String senderMail = StringUtils.hasText(query.getReceiveMail()) ? query.getReceiveMail() : query.getMail();
        String senderName = StringUtils.hasText(query.getReceiveName()) ? query.getReceiveName() : query.getName();
        if (!StringUtils.hasText(senderMail) && !StringUtils.hasText(senderName)) {
            return;
        }
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(senderMail)) {
            userWrapper.like(SysUser::getEmail, senderMail);
        }
        if (StringUtils.hasText(senderName)) {
            userWrapper.like(SysUser::getNickname, senderName);
        }
        List<Long> senderIds = userMapper.selectList(userWrapper).stream().map(SysUser::getId).toList();
        if (senderIds.isEmpty()) {
            wrapper.eq(MailUserBox::getMailId, -1L);
            return;
        }
        List<Long> mailIds = mailMessageMapper.selectList(
                new LambdaQueryWrapper<MailMessage>()
                        .select(MailMessage::getId)
                        .in(MailMessage::getSenderUserId, senderIds))
                .stream().map(MailMessage::getId).toList();
        if (mailIds.isEmpty()) {
            wrapper.eq(MailUserBox::getMailId, -1L);
            return;
        }
        wrapper.in(MailUserBox::getMailId, mailIds);
    }

    private InboxItemVO toInboxItem(MailUserBox box) {
        MailMessage message = mailMessageMapper.selectById(box.getMailId());
        SysUser sender = message == null ? null : userMapper.selectById(message.getSenderUserId());
        return new InboxItemVO(
                box.getMailId(),
                box.getStarFlag() == 1,
                hasAttachment(box.getMailId()),
                false,
                box.getReadFlag() == 1 ? 1 : 0,
                sender == null ? "未知用户" : sender.getNickname(),
                sender == null ? "" : sender.getEmail(),
                listLabels(box.getId()),
                message == null ? "" : message.getSubject(),
                buildPreview(message),
                box.getCreatedAt(),
                box.getReadAt(),
                box.getSpamReason(),
                box.getSpamScore(),
                box.getPriorityLevel(),
                box.getPriorityScore(),
                box.getPriorityReason()
        );
    }

    private OutboxItemVO toOutboxItem(MailUserBox box) {
        MailMessage message = mailMessageMapper.selectById(box.getMailId());
        return new OutboxItemVO(
                box.getMailId(),
                box.getStarFlag() == 1,
                hasAttachment(box.getMailId()),
                false,
                listRecipients(box.getMailId(), ROLE_TO, ROLE_CC),
                listLabels(box.getId()),
                message == null ? "" : message.getSubject(),
                message == null ? box.getCreatedAt() : message.getSentAt()
        );
    }

    private DraftItemVO toDraftItem(MailUserBox box) {
        MailMessage message = mailMessageMapper.selectById(box.getMailId());
        return new DraftItemVO(
                box.getMailId(),
                box.getStarFlag() == 1,
                hasAttachment(box.getMailId()),
                false,
                listRecipients(box.getMailId(), ROLE_TO, ROLE_CC),
                listLabels(box.getId()),
                message == null ? "" : message.getSubject(),
                message == null ? box.getCreatedAt() : message.getCreatedAt(),
                message == null ? box.getUpdatedAt() : message.getUpdatedAt()
        );
    }

    private MailListItemVO toMailListItem(MailUserBox box) {
        MailMessage message = mailMessageMapper.selectById(box.getMailId());
        SysUser sender = message == null ? null : userMapper.selectById(message.getSenderUserId());
        String type = BOX_OUTBOX.equals(box.getBoxType()) ? "send" : "receive";
        LocalDateTime date = message != null && message.getSentAt() != null ? message.getSentAt() : box.getUpdatedAt();
        return new MailListItemVO(
                box.getMailId(),
                box.getStarFlag() == 1,
                hasAttachment(box.getMailId()),
                false,
                type,
                sender == null ? "未知用户" : sender.getNickname(),
                sender == null ? "" : sender.getEmail(),
                listLabels(box.getId()),
                message == null ? "" : message.getSubject(),
                date
        );
    }

    // ── 批量加载辅助方法（消除 N+1 查询）──────────────────────────────

    private Map<Long, MailMessage> batchLoadMessages(List<Long> mailIds) {
        if (mailIds.isEmpty()) return Map.of();
        return mailMessageMapper.selectBatchIds(mailIds)
                .stream().collect(Collectors.toMap(MailMessage::getId, m -> m));
    }

    private Map<Long, SysUser> batchLoadSenders(Collection<MailMessage> messages) {
        List<Long> senderIds = messages.stream()
                .map(MailMessage::getSenderUserId).filter(Objects::nonNull).distinct().toList();
        if (senderIds.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(senderIds)
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    private Map<Long, List<LabelItemVO>> batchLoadLabels(List<Long> boxIds) {
        if (boxIds.isEmpty()) return Map.of();
        List<MailUserLabel> userLabels = userLabelMapper.selectList(
                new LambdaQueryWrapper<MailUserLabel>().in(MailUserLabel::getUserBoxId, boxIds));
        if (userLabels.isEmpty()) return Map.of();
        List<Long> labelIds = userLabels.stream().map(MailUserLabel::getLabelId).distinct().toList();
        Map<Long, MailLabel> labelById = labelMapper.selectBatchIds(labelIds)
                .stream().collect(Collectors.toMap(MailLabel::getId, l -> l));
        Map<Long, List<LabelItemVO>> result = new HashMap<>();
        for (MailUserLabel ul : userLabels) {
            MailLabel label = labelById.get(ul.getLabelId());
            if (label != null) {
                result.computeIfAbsent(ul.getUserBoxId(), k -> new ArrayList<>())
                        .add(new LabelItemVO(String.valueOf(label.getId()), String.valueOf(label.getId()),
                                label.getName(), label.getColor()));
            }
        }
        return result;
    }

    private Map<Long, List<PartyVO>> batchLoadRecipients(List<Long> mailIds, String... types) {
        if (mailIds.isEmpty()) return Map.of();
        List<String> typeList = List.of(types);
        List<MailRecipient> recipients = recipientMapper.selectList(
                new LambdaQueryWrapper<MailRecipient>()
                        .in(MailRecipient::getMailId, mailIds)
                        .in(MailRecipient::getRecipientType, typeList));
        Map<Long, List<PartyVO>> result = new HashMap<>();
        for (MailRecipient r : recipients) {
            result.computeIfAbsent(r.getMailId(), k -> new ArrayList<>())
                    .add(new PartyVO(r.getRecipientName(), r.getRecipientEmail()));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────

    private List<PartyVO> listRecipients(Long mailId, String... types) {
        List<String> typeList = List.of(types);
        return recipientMapper.selectList(new LambdaQueryWrapper<MailRecipient>()
                        .eq(MailRecipient::getMailId, mailId)
                        .in(MailRecipient::getRecipientType, typeList))
                .stream()
                .map(item -> new PartyVO(item.getRecipientName(), item.getRecipientEmail()))
                .toList();
    }

    private List<AttachmentItemVO> listAttachments(Long mailId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<MailAttachment>()
                        .eq(MailAttachment::getMailId, mailId))
                .stream()
                .map(item -> new AttachmentItemVO(
                        item.getId(),
                        item.getOriginalName(),
                        publicUrlBuilder.attachmentDownloadUrl(item.getId()),
                        item.getFileSize(),
                        item.getContentType()))
                .toList();
    }

    private List<LabelItemVO> listLabels(Long userBoxId) {
        List<Long> labelIds = getLabelIds(userBoxId);
        if (labelIds.isEmpty()) {
            return List.of();
        }
        List<MailLabel> labels = labelMapper.selectList(new LambdaQueryWrapper<MailLabel>()
                .in(MailLabel::getId, labelIds));
        return labels.stream()
                .map(item -> new LabelItemVO(String.valueOf(item.getId()), String.valueOf(item.getId()), item.getName(), item.getColor()))
                .toList();
    }

    private List<Long> getLabelIds(Long userBoxId) {
        return userLabelMapper.selectList(new LambdaQueryWrapper<MailUserLabel>()
                        .eq(MailUserLabel::getUserBoxId, userBoxId))
                .stream()
                .map(MailUserLabel::getLabelId)
                .toList();
    }

    private boolean hasAttachment(Long mailId) {
        Long count = attachmentMapper.selectCount(new LambdaQueryWrapper<MailAttachment>()
                .eq(MailAttachment::getMailId, mailId));
        return count != null && count > 0;
    }

    private MailUserBox findBoxByMailId(Long mailId) {
        return findBoxByMailId(mailId, null);
    }

    private MailUserBox findBoxByMailId(Long mailId, String boxType) {
        Long userId = requiredUserId();
        String normalizedBoxType = normalizeBoxType(boxType);
        MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getMailId, mailId)
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(StringUtils.hasText(normalizedBoxType), MailUserBox::getBoxType, normalizedBoxType)
                .last("limit 1"));
        if (box == null) {
            throw new BusinessException(404, "邮件不存在");
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
            throw new BusinessException(404, "草稿不存在");
        }
        MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getMailId, message.getId())
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getBoxType, BOX_DRAFT)
                .last("limit 1"));
        if (box == null) {
            throw new BusinessException(403, "无权修改该草稿");
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
        if (!StringUtils.hasText(boxType)) {
            return null;
        }
        String value = boxType.trim().toUpperCase();
        return switch (value) {
            case BOX_INBOX, "RECEIVE" -> BOX_INBOX;
            case BOX_OUTBOX, "SEND" -> BOX_OUTBOX;
            case BOX_DRAFT, "DRAFTBOX" -> BOX_DRAFT;
            default -> null;
        };
    }

    private Long requiredUserId() {
        Long userId = UserContext.requireUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private String buildPreview(MailMessage message) {
        if (message == null) {
            return "";
        }
        String text = StringUtils.hasText(message.getContentText())
                ? message.getContentText()
                : stripHtml(message.getContentHtml());
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

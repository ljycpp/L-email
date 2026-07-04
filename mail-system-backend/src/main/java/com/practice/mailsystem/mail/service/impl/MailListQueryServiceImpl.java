package com.practice.mailsystem.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.practice.mailsystem.attachment.entity.MailAttachment;
import com.practice.mailsystem.attachment.mapper.MailAttachmentMapper;
import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.PageResult;
import com.practice.mailsystem.common.PublicUrlBuilder;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.mail.dto.MailListQuery;
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
import com.practice.mailsystem.mail.service.support.MailUserBoxQuerySupport;
import com.practice.mailsystem.mail.util.MailBoxUtils;
import com.practice.mailsystem.mail.vo.AttachmentItemVO;
import com.practice.mailsystem.mail.vo.DraftItemVO;
import com.practice.mailsystem.mail.vo.InboxItemVO;
import com.practice.mailsystem.mail.vo.LabelItemVO;
import com.practice.mailsystem.mail.vo.MailDetailVO;
import com.practice.mailsystem.mail.vo.MailListItemVO;
import com.practice.mailsystem.mail.vo.OutboxItemVO;
import com.practice.mailsystem.mail.vo.PartyVO;
import com.practice.mailsystem.user.entity.SysUser;
import com.practice.mailsystem.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MailListQueryServiceImpl implements MailListQueryService {

    private static final String BOX_INBOX  = "INBOX";
    private static final String BOX_OUTBOX = "OUTBOX";
    private static final String BOX_DRAFT  = "DRAFT";
    private static final String ROLE_TO    = "TO";
    private static final String ROLE_CC    = "CC";

    private final MailMessageMapper mailMessageMapper;
    private final MailUserBoxMapper mailUserBoxMapper;
    private final MailRecipientMapper recipientMapper;
    private final MailAttachmentMapper attachmentMapper;
    private final MailLabelMapper labelMapper;
    private final MailUserLabelMapper userLabelMapper;
    private final SysUserMapper userMapper;
    private final PublicUrlBuilder publicUrlBuilder;
    private final MailUserBoxQuerySupport boxQuerySupport;

    @Override
    public PageResult<InboxItemVO> listInbox(MailListQuery query) {
        Page<MailUserBox> page = queryBox(query, BOX_INBOX, false);
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
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
                    sender == null ? "????" : sender.getNickname(),
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
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
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
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
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

        Long userId = UserContext.requireUserId();
        LambdaQueryWrapper<MailUserBox> wrapper = new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getDeletedFlag, 1)
                .orderByDesc(MailUserBox::getUpdatedAt);
        applyTrashListFilters(wrapper, query, userId);
        Page<MailUserBox> page = mailUserBoxMapper.selectPage(new Page<>(query.getPage(), query.getLimit()), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
        List<MailListItemVO> items = toMailListItems(page.getRecords());
        return new PageResult<>(page.getTotal(), items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MailDetailVO getDetail(Long mailId, String mailType) {
        Long userId = UserContext.requireUserId();
        String boxType = normalizeBoxType(mailType);
        MailUserBox box = mailUserBoxMapper.selectOne(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getMailId, mailId)
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(StringUtils.hasText(boxType), MailUserBox::getBoxType, boxType)
                .last("limit 1"));
        if (box == null) {
            throw new BusinessException(404, "?????");
        }
        if (BOX_INBOX.equals(boxType) && box.getReadFlag() == 0) {
            box.setReadFlag(1);
            box.setReadAt(LocalDateTime.now());
            box.setUpdatedAt(LocalDateTime.now());
            mailUserBoxMapper.updateById(box);
        }

        MailMessage message = mailMessageMapper.selectById(mailId);
        if (message == null) {
            throw new BusinessException(404, "?????");
        }
        SysUser sender = userMapper.selectById(message.getSenderUserId());
        return new MailDetailVO(
                message.getId(),
                message.getSubject(),
                message.getContentHtml(),
                sender == null ? "????" : sender.getNickname(),
                sender == null ? "" : sender.getEmail(),
                box.getCreatedAt(),
                message.getSentAt(),
                box.getStarFlag() == 1,
                listRecipients(mailId, ROLE_TO),
                listRecipients(mailId, ROLE_CC),
                listAttachments(mailId),
                List.of(),
                listLabels(box.getId()),
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
    public long countUnreadInbox() {
        Long userId = UserContext.requireUserId();
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
        Long userId = UserContext.requireUserId();
        Long count = mailUserBoxMapper.selectCount(new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getBoxType, BOX_INBOX)
                .eq(MailUserBox::getDeletedFlag, 0)
                .eq(MailUserBox::getSpamFlag, 1));
        return count == null ? 0L : count;
    }

    private PageResult<MailListItemVO> listByLabel(MailListQuery query) {
        Long userId = UserContext.requireUserId();
        LambdaQueryWrapper<MailUserBox> wrapper = new LambdaQueryWrapper<MailUserBox>()
                .eq(MailUserBox::getOwnerUserId, userId)
                .eq(MailUserBox::getDeletedFlag, 0)
                .orderByDesc(MailUserBox::getUpdatedAt);
        applyTrashListFilters(wrapper, query, userId);
        Page<MailUserBox> page = mailUserBoxMapper.selectPage(new Page<>(query.getPage(), query.getLimit()), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
        List<MailListItemVO> items = toMailListItems(page.getRecords());
        return new PageResult<>(page.getTotal(), items);
    }

    private Page<MailUserBox> queryBox(MailListQuery query, String boxType, boolean deleted) {
        Long userId = UserContext.requireUserId();
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
        boxQuerySupport.applyKeywordFilter(wrapper, query.getTitle());
        boxQuerySupport.applyLabelFilter(wrapper, query.getLabelId(), userId);
        if (BOX_INBOX.equals(boxType)) {
            applyInboxSenderFilter(wrapper, query);
        } else if (BOX_OUTBOX.equals(boxType) || BOX_DRAFT.equals(boxType)) {
            applyRecipientFilter(wrapper, query);
        }
        return mailUserBoxMapper.selectPage(new Page<>(query.getPage(), query.getLimit()), wrapper);
    }

    private void applyInboxSenderFilter(LambdaQueryWrapper<MailUserBox> wrapper, MailListQuery query) {
        String senderMail = StringUtils.hasText(query.getReceiveMail()) ? query.getReceiveMail() : query.getMail();
        String senderName = StringUtils.hasText(query.getReceiveName()) ? query.getReceiveName() : query.getName();
        boxQuerySupport.applyInboxSenderFilter(wrapper, senderMail, senderName);
    }

    private void applyRecipientFilter(LambdaQueryWrapper<MailUserBox> wrapper, MailListQuery query) {
        String recipientMail = StringUtils.hasText(query.getReceiveMail()) ? query.getReceiveMail() : query.getMail();
        String recipientName = StringUtils.hasText(query.getReceiveName()) ? query.getReceiveName() : query.getName();
        boxQuerySupport.applyRecipientFilter(wrapper, recipientMail, recipientName);
    }

    private void applyTrashListFilters(LambdaQueryWrapper<MailUserBox> wrapper, MailListQuery query, Long userId) {
        boxQuerySupport.applyKeywordFilter(wrapper, query.getTitle());
        boxQuerySupport.applyLabelFilter(wrapper, query.getLabelId(), userId);
        if (query.getStatus() != null) {
            if (query.getStatus() == 0) {
                wrapper.eq(MailUserBox::getReadFlag, 0);
            } else if (query.getStatus() == 1) {
                wrapper.eq(MailUserBox::getReadFlag, 1);
            }
        }
        applyInboxSenderFilter(wrapper, query);
    }

    /**
     * ???? MailListItemVO??? N+1 ???
     */
    private List<MailListItemVO> toMailListItems(List<MailUserBox> boxes) {
        List<Long> mailIds = boxes.stream().map(MailUserBox::getMailId).distinct().toList();
        Map<Long, MailMessage> msgMap = batchLoadMessages(mailIds);
        Map<Long, SysUser> userMap = batchLoadSenders(msgMap.values());
        Map<Long, List<LabelItemVO>> labelMap = batchLoadLabels(
                boxes.stream().map(MailUserBox::getId).toList());
        Set<Long> mailsWithAttachment = batchCheckAttachments(mailIds);

        return boxes.stream().map(box -> {
            MailMessage msg = msgMap.get(box.getMailId());
            SysUser sender = msg != null ? userMap.get(msg.getSenderUserId()) : null;
            String type = BOX_OUTBOX.equals(box.getBoxType()) ? "send" : "receive";
            LocalDateTime date = msg != null && msg.getSentAt() != null ? msg.getSentAt() : box.getUpdatedAt();
            return new MailListItemVO(
                    box.getMailId(),
                    box.getStarFlag() == 1,
                    mailsWithAttachment.contains(box.getMailId()),
                    false,
                    type,
                    sender == null ? "????" : sender.getNickname(),
                    sender == null ? "" : sender.getEmail(),
                    labelMap.getOrDefault(box.getId(), List.of()),
                    msg == null ? "" : msg.getSubject(),
                    date
            );
        }).toList();
    }

    private Map<Long, MailMessage> batchLoadMessages(List<Long> mailIds) {
        if (mailIds.isEmpty()) {
            return Map.of();
        }
        return mailMessageMapper.selectBatchIds(mailIds)
                .stream().collect(Collectors.toMap(MailMessage::getId, m -> m));
    }

    private Map<Long, SysUser> batchLoadSenders(Collection<MailMessage> messages) {
        List<Long> senderIds = messages.stream()
                .map(MailMessage::getSenderUserId).filter(Objects::nonNull).distinct().toList();
        if (senderIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(senderIds)
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    private Map<Long, List<LabelItemVO>> batchLoadLabels(List<Long> boxIds) {
        if (boxIds.isEmpty()) {
            return Map.of();
        }
        List<MailUserLabel> userLabels = userLabelMapper.selectList(
                new LambdaQueryWrapper<MailUserLabel>().in(MailUserLabel::getUserBoxId, boxIds));
        if (userLabels.isEmpty()) {
            return Map.of();
        }
        List<Long> labelIds = userLabels.stream().map(MailUserLabel::getLabelId).distinct().toList();
        Map<Long, MailLabel> labelById = labelMapper.selectBatchIds(labelIds)
                .stream().collect(Collectors.toMap(MailLabel::getId, l -> l));
        Map<Long, List<LabelItemVO>> result = new HashMap<>();
        for (MailUserLabel ul : userLabels) {
            MailLabel label = labelById.get(ul.getLabelId());
            if (label != null) {
                result.computeIfAbsent(ul.getUserBoxId(), k -> new ArrayList<>())
                        .add(toLabelItemVO(label));
            }
        }
        return result;
    }

    private Map<Long, List<PartyVO>> batchLoadRecipients(List<Long> mailIds, String... types) {
        if (mailIds.isEmpty()) {
            return Map.of();
        }
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
        List<Long> labelIds = userLabelMapper.selectList(new LambdaQueryWrapper<MailUserLabel>()
                        .eq(MailUserLabel::getUserBoxId, userBoxId))
                .stream()
                .map(MailUserLabel::getLabelId)
                .toList();
        if (labelIds.isEmpty()) {
            return List.of();
        }
        return labelMapper.selectList(new LambdaQueryWrapper<MailLabel>()
                        .in(MailLabel::getId, labelIds))
                .stream()
                .map(this::toLabelItemVO)
                .toList();
    }

    /**
     * ?????? mailId ??????????? mailId ??????? COUNT ???
     */
    private Set<Long> batchCheckAttachments(List<Long> mailIds) {
        if (mailIds.isEmpty()) {
            return Set.of();
        }
        return attachmentMapper.selectList(new LambdaQueryWrapper<MailAttachment>()
                        .in(MailAttachment::getMailId, mailIds)
                        .select(MailAttachment::getMailId))
                .stream()
                .map(MailAttachment::getMailId)
                .collect(Collectors.toSet());
    }

    private LabelItemVO toLabelItemVO(MailLabel label) {
        String idStr = String.valueOf(label.getId());
        return new LabelItemVO(idStr, idStr, label.getName(), label.getColor());
    }

    private String buildPreview(MailMessage message) {
        if (message == null) {
            return "";
        }
        String text = StringUtils.hasText(message.getContentText())
                ? message.getContentText()
                : MailBoxUtils.stripHtml(message.getContentHtml());
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 80) + "?" : text;
    }

    private String normalizeBoxType(String boxType) {
        return MailBoxUtils.normalizeBoxType(boxType);
    }
}

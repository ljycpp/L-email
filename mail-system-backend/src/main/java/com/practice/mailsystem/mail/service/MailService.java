package com.practice.mailsystem.mail.service;

import com.practice.mailsystem.common.PageResult;
import com.practice.mailsystem.mail.dto.LegacyMailFormRequest;
import com.practice.mailsystem.mail.dto.MailCreateRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import com.practice.mailsystem.mail.vo.DraftItemVO;
import com.practice.mailsystem.mail.vo.InboxItemVO;
import com.practice.mailsystem.mail.vo.MailDetailVO;
import com.practice.mailsystem.mail.vo.MailListItemVO;
import com.practice.mailsystem.mail.vo.OutboxItemVO;

import java.util.Map;

public interface MailService {

    Long send(MailCreateRequest request);

    Long saveDraft(MailCreateRequest request);

    Long sendLegacyForm(LegacyMailFormRequest request);

    Long saveLegacyDraft(LegacyMailFormRequest request);

    PageResult<InboxItemVO> listInbox(MailListQuery query);

    PageResult<OutboxItemVO> listOutbox(MailListQuery query);

    PageResult<DraftItemVO> listDrafts(MailListQuery query);

    PageResult<MailListItemVO> listByMailList(MailListQuery query, Map<String, String> requestParams);

    MailDetailVO getDetail(Long mailId, String mailType);

    void deleteMail(Long mailId);

    void undoDelete(Long mailId);

    void deleteMails(Iterable<Long> mailIds);

    void deleteMails(Iterable<Long> mailIds, String boxType);

    void undoDeleteMails(Iterable<Long> mailIds);

    void undoDeleteMails(Iterable<Long> mailIds, String boxType);

    void deletePermanently(Iterable<Long> mailIds);

    void deletePermanently(Iterable<Long> mailIds, String boxType);

    void markRead(Long mailId);

    void toggleStar(Long mailId);

    void toggleStars(Iterable<Long> mailIds);

    void toggleStars(Iterable<Long> mailIds, String boxType);

    void markLabel(Long labelId, Iterable<Long> mailIds);

    void markLabel(Long labelId, Iterable<Long> mailIds, String boxType);

    void markAllInboxRead();

    void markAsSpam(Iterable<Long> mailIds);

    void unmarkSpam(Iterable<Long> mailIds);

    long countUnreadInbox();

    long countSpam();
}

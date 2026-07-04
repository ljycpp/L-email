package com.practice.mailsystem.mail.service;

import com.practice.mailsystem.common.PageResult;
import com.practice.mailsystem.mail.dto.MailListQuery;
import com.practice.mailsystem.mail.vo.DraftItemVO;
import com.practice.mailsystem.mail.vo.InboxItemVO;
import com.practice.mailsystem.mail.vo.MailDetailVO;
import com.practice.mailsystem.mail.vo.MailListItemVO;
import com.practice.mailsystem.mail.vo.OutboxItemVO;

import java.util.Map;

/** 邮件列表与详情查询（从 MailServiceImpl 拆分，便于维护与测试） */
public interface MailListQueryService {

    PageResult<InboxItemVO> listInbox(MailListQuery query);

    PageResult<OutboxItemVO> listOutbox(MailListQuery query);

    PageResult<DraftItemVO> listDrafts(MailListQuery query);

    PageResult<MailListItemVO> listByMailList(MailListQuery query, Map<String, String> requestParams);

    MailDetailVO getDetail(Long mailId, String mailType);

    long countUnreadInbox();

    long countSpam();
}

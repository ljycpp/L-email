package com.practice.mailsystem.mail.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.mailsystem.mail.entity.MailUserBox;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * mail_user_box 列表查询条件构建（EXISTS 子查询，避免先查 ID 再 IN）。
 */
@Component
public class MailUserBoxQuerySupport {

    private static final String ROLE_TO = "TO";
    private static final String ROLE_CC = "CC";

    public void applyKeywordFilter(LambdaQueryWrapper<MailUserBox> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String trimmed = keyword.trim();
        String fullText = toFullTextBooleanQuery(trimmed);
        if (StringUtils.hasText(fullText)) {
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM mail_message m WHERE m.id = mail_user_box.mail_id "
                            + "AND MATCH(m.subject, m.content_text) AGAINST({0} IN BOOLEAN MODE))",
                    fullText);
            return;
        }
        String pattern = "%" + trimmed + "%";
        wrapper.apply(
                "EXISTS (SELECT 1 FROM mail_message m WHERE m.id = mail_user_box.mail_id "
                        + "AND (m.subject LIKE {0} OR m.content_text LIKE {1}))",
                pattern, pattern);
    }

    public void applyLabelFilter(LambdaQueryWrapper<MailUserBox> wrapper, Long labelId, Long userId) {
        if (labelId == null) {
            return;
        }
        wrapper.apply(
                "EXISTS (SELECT 1 FROM mail_user_label ul "
                        + "INNER JOIN mail_label l ON l.id = ul.label_id "
                        + "WHERE ul.user_box_id = mail_user_box.id "
                        + "AND ul.label_id = {0} AND l.user_id = {1})",
                labelId, userId);
    }

    public void applyInboxSenderFilter(LambdaQueryWrapper<MailUserBox> wrapper,
                                       String senderMail,
                                       String senderName) {
        if (!StringUtils.hasText(senderMail) && !StringUtils.hasText(senderName)) {
            return;
        }
        String mailPattern = StringUtils.hasText(senderMail) ? "%" + senderMail.trim() + "%" : null;
        String namePattern = StringUtils.hasText(senderName) ? "%" + senderName.trim() + "%" : null;
        if (mailPattern != null && namePattern != null) {
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM mail_message m "
                            + "INNER JOIN sys_user u ON u.id = m.sender_user_id "
                            + "WHERE m.id = mail_user_box.mail_id "
                            + "AND u.email LIKE {0} AND u.nickname LIKE {1})",
                    mailPattern, namePattern);
        } else if (mailPattern != null) {
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM mail_message m "
                            + "INNER JOIN sys_user u ON u.id = m.sender_user_id "
                            + "WHERE m.id = mail_user_box.mail_id AND u.email LIKE {0})",
                    mailPattern);
        } else {
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM mail_message m "
                            + "INNER JOIN sys_user u ON u.id = m.sender_user_id "
                            + "WHERE m.id = mail_user_box.mail_id AND u.nickname LIKE {0})",
                    namePattern);
        }
    }

    public void applyRecipientFilter(LambdaQueryWrapper<MailUserBox> wrapper,
                                     String recipientMail,
                                     String recipientName) {
        if (!StringUtils.hasText(recipientMail) && !StringUtils.hasText(recipientName)) {
            return;
        }
        String mailPattern = StringUtils.hasText(recipientMail) ? "%" + recipientMail.trim() + "%" : null;
        String namePattern = StringUtils.hasText(recipientName) ? "%" + recipientName.trim() + "%" : null;
        String recipientTypeIn = "AND r.recipient_type IN ({0},{1}) ";
        if (mailPattern != null && namePattern != null) {
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM mail_recipient r "
                            + "WHERE r.mail_id = mail_user_box.mail_id "
                            + recipientTypeIn
                            + "AND r.recipient_email LIKE {2} AND r.recipient_name LIKE {3})",
                    ROLE_TO, ROLE_CC, mailPattern, namePattern);
        } else if (mailPattern != null) {
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM mail_recipient r "
                            + "WHERE r.mail_id = mail_user_box.mail_id "
                            + recipientTypeIn
                            + "AND r.recipient_email LIKE {2})",
                    ROLE_TO, ROLE_CC, mailPattern);
        } else {
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM mail_recipient r "
                            + "WHERE r.mail_id = mail_user_box.mail_id "
                            + recipientTypeIn
                            + "AND r.recipient_name LIKE {2})",
                    ROLE_TO, ROLE_CC, namePattern);
        }
    }

    /** 长度 ≥ 3 的词才参与 FULLTEXT（MySQL 默认 ft_min_word_len=4，短词回退 LIKE） */
    String toFullTextBooleanQuery(String keyword) {
        String query = Arrays.stream(keyword.split("\\s+"))
                .map(String::trim)
                .filter(part -> part.length() >= 3)
                .map(part -> part.replaceAll("[+\\-><()~*\"@]+", ""))
                .filter(StringUtils::hasText)
                .map(part -> "+" + part + "*")
                .collect(Collectors.joining(" "));
        return StringUtils.hasText(query) ? query : null;
    }
}

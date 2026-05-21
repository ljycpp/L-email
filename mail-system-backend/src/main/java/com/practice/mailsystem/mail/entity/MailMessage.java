package com.practice.mailsystem.mail.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_message")
public class MailMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String subject;

    private String contentHtml;

    private String contentText;

    private Long senderUserId;

    private Integer draftFlag;

    private Integer hasAttachment;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

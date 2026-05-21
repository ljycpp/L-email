package com.practice.mailsystem.mail.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_recipient")
public class MailRecipient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mailId;

    private Long recipientUserId;

    private String recipientName;

    private String recipientEmail;

    private String recipientType;

    private LocalDateTime createdAt;
}

package com.practice.mailsystem.attachment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_attachment")
public class MailAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mailId;

    private Long ownerUserId;

    private String originalName;

    private String storedName;

    private String storagePath;

    private String contentType;

    private Long fileSize;

    private String fileHash;

    private Integer tempFlag;

    private LocalDateTime createdAt;
}

package com.practice.mailsystem.directory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_contact")
public class MailContact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerUserId;

    private Long groupId;

    private Long contactUserId;

    private String contactName;

    private String contactEmail;

    private String avatarUrl;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

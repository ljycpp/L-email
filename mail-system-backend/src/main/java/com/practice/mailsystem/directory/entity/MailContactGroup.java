package com.practice.mailsystem.directory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_contact_group")
public class MailContactGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerUserId;

    private String groupName;

    private LocalDateTime createdAt;
}

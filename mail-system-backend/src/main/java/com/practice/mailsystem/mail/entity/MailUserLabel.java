package com.practice.mailsystem.mail.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_user_label")
public class MailUserLabel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userBoxId;

    private Long labelId;

    private LocalDateTime createdAt;
}

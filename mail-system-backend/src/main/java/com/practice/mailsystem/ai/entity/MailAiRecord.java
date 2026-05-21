package com.practice.mailsystem.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_ai_record")
public class MailAiRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long mailId;

    private String actionType;

    private String promptSnapshot;

    private String resultJson;

    private String status;

    private String errorMessage;

    private LocalDateTime createdAt;
}

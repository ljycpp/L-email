package com.practice.mailsystem.mail.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_user_box")
public class MailUserBox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mailId;

    private Long ownerUserId;

    private String boxType;

    private String roleType;

    private Integer readFlag;

    private LocalDateTime readAt;

    private Integer starFlag;

    private Integer deletedFlag;

    private Integer importantFlag;

    private Integer spamFlag;

    private String spamReason;

    private Double spamScore;

    private LocalDateTime spamDetectedAt;

    private Double priorityScore;

    private String priorityLevel;

    private String priorityReason;

    private LocalDateTime priorityScoredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.practice.mailsystem.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_ai_config")
public class UserAiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String apiKeyEncrypted;

    private String modelName;

    private Integer enabled;

    private String lastTestStatus;

    private String lastTestMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

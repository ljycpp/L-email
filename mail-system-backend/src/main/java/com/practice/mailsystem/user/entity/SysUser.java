package com.practice.mailsystem.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private Integer status;

    private String introduction;

    /** 1=开启智能垃圾邮件过滤 */
    private Integer autoSpamFilter;

    /** 1=开启智能邮件优先级 */
    private Integer autoPriorityFilter;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String roleName;
}

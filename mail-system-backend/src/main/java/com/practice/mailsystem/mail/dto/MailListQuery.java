package com.practice.mailsystem.mail.dto;

import lombok.Data;

@Data
public class MailListQuery {

    private Long page = 1L;
    private Long limit = 20L;
    private String title;
    private Integer status;
    private String receiveName;
    private String receiveMail;
    private Long startCreateDate;
    private Long stopCreateDate;
    private Long startModifyDate;
    private Long stopModifyDate;
    private Long startDate;
    private Long stopDate;
    private String type;
    private String sort;
    private String order;
    private String name;
    private String mail;
    private Long groupId;
    /** 1=仅星标邮件 */
    private Integer starred;
    /** 1=仅垃圾箱；收件箱默认排除垃圾箱 */
    private Integer spam;
    /** 按标签筛选（user_box 关联的标签 ID） */
    private Long labelId;
}

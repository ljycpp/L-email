package com.practice.mailsystem.mail.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MailListQuery {

    @Min(1)
    private int page = 1;
    @Min(1) @Max(100)
    private int limit = 20;

    /** 关键字搜索（主题/正文） */
    private String title;

    /** 读取状态：0=未读，1=已读，null=全部 */
    private Integer status;

    /** 发件人/收件人姓名（模糊匹配） */
    private String receiveName;

    /** 发件人/收件人邮箱（模糊匹配） */
    private String receiveMail;

    /** receiveName/receiveMail 的别名，兼容旧版前端 */
    private String name;
    private String mail;

    /** 按通讯录分组筛选（通讯录模块专用） */
    private Long groupId;

    /** 1=仅星标邮件 */
    private Integer starred;

    /** 1=仅垃圾箱；收件箱默认排除垃圾箱 */
    private Integer spam;

    /** 按标签筛选（user_box 关联的标签 ID） */
    private Long labelId;
}

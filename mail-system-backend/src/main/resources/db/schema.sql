-- ============================================================
--  L-email 数据库完整建表脚本
--  数据库: mail_system
--  编码:   UTF-8 / utf8mb4
--  说明:   幂等脚本（IF NOT EXISTS），可重复执行
--  表结构分为四个模块：
--    1. 用户模块      sys_user
--    2. 邮件核心模块  mail_message / mail_user_box / mail_recipient
--    3. 附件与标签    mail_attachment / mail_label / mail_user_label
--    4. 通讯录模块    mail_contact_group / mail_contact
--    5. AI 模块      user_ai_config / mail_ai_record
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1. 用户模块
-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS sys_user (
    id                  BIGINT       PRIMARY KEY AUTO_INCREMENT,
    email               VARCHAR(100) NOT NULL UNIQUE    COMMENT '登录邮箱（系统域名格式）',
    password_hash       VARCHAR(255) NOT NULL           COMMENT 'BCrypt 哈希密码',
    nickname            VARCHAR(50)  NOT NULL           COMMENT '昵称',
    avatar_url          VARCHAR(255)                    COMMENT '头像地址',
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：1正常 0禁用',
    introduction        VARCHAR(255)                    COMMENT '个人简介',
    auto_spam_filter    TINYINT      NOT NULL DEFAULT 0 COMMENT '智能垃圾过滤开关：1开启',
    auto_priority_filter TINYINT     NOT NULL DEFAULT 0 COMMENT '智能优先级评分开关：1开启',
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME     NOT NULL
);

-- ────────────────────────────────────────────────────────────
-- 2. 邮件核心模块
-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS mail_message (
    id              BIGINT    PRIMARY KEY AUTO_INCREMENT,
    subject         VARCHAR(255) NOT NULL              COMMENT '邮件主题',
    content_html    LONGTEXT     NOT NULL              COMMENT 'HTML 正文',
    content_text    TEXT                               COMMENT '纯文本正文（用于搜索和预览）',
    sender_user_id  BIGINT       NOT NULL              COMMENT '发件人 ID',
    draft_flag      TINYINT      NOT NULL DEFAULT 0    COMMENT '是否草稿：1草稿 0已发送',
    has_attachment  TINYINT      NOT NULL DEFAULT 0    COMMENT '是否含附件：1是',
    sent_at         DATETIME     NULL                  COMMENT '实际发送时间',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    INDEX idx_mail_message_sender (sender_user_id),
    FULLTEXT INDEX idx_mail_message_search (subject, content_text),
    CONSTRAINT fk_mail_message_sender FOREIGN KEY (sender_user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_user_box (
    id                  BIGINT      PRIMARY KEY AUTO_INCREMENT    COMMENT '用户邮箱视图行（每封邮件对每个用户独立一行）',
    mail_id             BIGINT      NOT NULL                      COMMENT '关联邮件',
    owner_user_id       BIGINT      NOT NULL                      COMMENT '所属用户',
    box_type            VARCHAR(20) NOT NULL                      COMMENT '邮箱类型：INBOX / OUTBOX / DRAFT',
    role_type           VARCHAR(20) NOT NULL                      COMMENT '角色：SENDER / TO / CC',
    read_flag           TINYINT     NOT NULL DEFAULT 0            COMMENT '已读标记',
    read_at             DATETIME    NULL                          COMMENT '阅读时间',
    star_flag           TINYINT     NOT NULL DEFAULT 0            COMMENT '星标标记',
    deleted_flag        TINYINT     NOT NULL DEFAULT 0            COMMENT '软删除（回收站）',
    important_flag      TINYINT     NOT NULL DEFAULT 0            COMMENT '重要标记（预留）',
    spam_flag           TINYINT     NOT NULL DEFAULT 0            COMMENT '垃圾邮件标记',
    spam_reason         VARCHAR(500)                              COMMENT '垃圾判定原因',
    spam_score          DECIMAL(6,4)                              COMMENT '垃圾得分 [0,1]',
    spam_detected_at    DATETIME                                  COMMENT '垃圾检测时间',
    priority_score      DOUBLE      DEFAULT 0                     COMMENT '优先级得分',
    priority_level      VARCHAR(16)                               COMMENT '优先级等级：HIGH / NORMAL / LOW',
    priority_reason     VARCHAR(500)                              COMMENT '优先级判定原因',
    priority_scored_at  DATETIME                                  COMMENT '优先级评分时间',
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME    NOT NULL,
    INDEX idx_mail_user_box_owner (owner_user_id, box_type, deleted_flag),
    CONSTRAINT fk_mail_user_box_mail  FOREIGN KEY (mail_id)       REFERENCES mail_message(id),
    CONSTRAINT fk_mail_user_box_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_recipient (
    id                  BIGINT      PRIMARY KEY AUTO_INCREMENT,
    mail_id             BIGINT      NOT NULL              COMMENT '关联邮件',
    recipient_user_id   BIGINT      NULL                  COMMENT '收件人系统用户 ID（站外收件时为 NULL）',
    recipient_name      VARCHAR(50) NOT NULL              COMMENT '收件人姓名',
    recipient_email     VARCHAR(100) NOT NULL             COMMENT '收件人邮箱',
    recipient_type      VARCHAR(10) NOT NULL              COMMENT '类型：TO / CC',
    created_at          DATETIME    NOT NULL,
    INDEX idx_mail_recipient_mail (mail_id),
    INDEX idx_mail_recipient_email (recipient_email),
    CONSTRAINT fk_mail_recipient_mail FOREIGN KEY (mail_id) REFERENCES mail_message(id)
);

-- ────────────────────────────────────────────────────────────
-- 3. 附件与标签
-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS mail_attachment (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    mail_id         BIGINT       NULL                    COMMENT '关联邮件（上传后暂存时为 NULL）',
    owner_user_id   BIGINT       NOT NULL                COMMENT '上传用户',
    original_name   VARCHAR(255) NOT NULL                COMMENT '原始文件名',
    stored_name     VARCHAR(255) NOT NULL                COMMENT '存储文件名（UUID）',
    storage_path    VARCHAR(500) NOT NULL                COMMENT '服务器磁盘绝对路径',
    content_type    VARCHAR(100)                         COMMENT 'MIME 类型',
    file_size       BIGINT       NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
    file_hash       VARCHAR(64)                          COMMENT 'SHA-256 哈希（预留去重）',
    temp_flag       TINYINT      NOT NULL DEFAULT 1      COMMENT '临时标记：1未关联邮件 0已关联',
    created_at      DATETIME     NOT NULL,
    INDEX idx_mail_attachment_mail  (mail_id),
    INDEX idx_mail_attachment_owner (owner_user_id),
    CONSTRAINT fk_mail_attachment_mail  FOREIGN KEY (mail_id)       REFERENCES mail_message(id),
    CONSTRAINT fk_mail_attachment_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_label (
    id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL              COMMENT '所属用户',
    name        VARCHAR(50) NOT NULL              COMMENT '标签名称',
    color       VARCHAR(20) NOT NULL              COMMENT '标签颜色（HEX）',
    created_at  DATETIME    NOT NULL,
    UNIQUE KEY uk_mail_label_user_name (user_id, name),
    CONSTRAINT fk_mail_label_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_user_label (
    id          BIGINT   PRIMARY KEY AUTO_INCREMENT,
    user_box_id BIGINT   NOT NULL              COMMENT '关联的用户邮箱视图行',
    label_id    BIGINT   NOT NULL              COMMENT '关联标签',
    created_at  DATETIME NOT NULL,
    UNIQUE KEY uk_mail_user_label (user_box_id, label_id),
    CONSTRAINT fk_mail_user_label_box   FOREIGN KEY (user_box_id) REFERENCES mail_user_box(id),
    CONSTRAINT fk_mail_user_label_label FOREIGN KEY (label_id)    REFERENCES mail_label(id)
);

-- ────────────────────────────────────────────────────────────
-- 4. 通讯录模块
-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS mail_contact_group (
    id              BIGINT      PRIMARY KEY AUTO_INCREMENT,
    owner_user_id   BIGINT      NOT NULL              COMMENT '所属用户',
    group_name      VARCHAR(50) NOT NULL              COMMENT '分组名称',
    created_at      DATETIME    NOT NULL,
    UNIQUE KEY uk_mail_contact_group_owner_name (owner_user_id, group_name),
    CONSTRAINT fk_mail_contact_group_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_contact (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    owner_user_id   BIGINT       NOT NULL              COMMENT '所属用户',
    group_id        BIGINT       NULL                  COMMENT '所属分组（NULL 为未分组）',
    contact_user_id BIGINT       NULL                  COMMENT '对应系统用户 ID（系统内联系人）',
    contact_name    VARCHAR(50)  NOT NULL              COMMENT '联系人姓名',
    contact_email   VARCHAR(100) NOT NULL              COMMENT '联系人邮箱',
    avatar_url      VARCHAR(255)                       COMMENT '头像',
    remark          VARCHAR(255)                       COMMENT '备注',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    INDEX idx_mail_contact_owner (owner_user_id),
    UNIQUE KEY uk_mail_contact_owner_email (owner_user_id, contact_email),
    CONSTRAINT fk_mail_contact_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_mail_contact_group FOREIGN KEY (group_id)      REFERENCES mail_contact_group(id)
);

-- ────────────────────────────────────────────────────────────
-- 5. AI 模块
-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS user_ai_config (
    id                  BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL                COMMENT '用户 ID（唯一）',
    api_key_encrypted   VARCHAR(512) NOT NULL                COMMENT 'AES 加密后的 API Key',
    model_name          VARCHAR(100) NOT NULL                COMMENT '模型名称',
    enabled             TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用：1是',
    last_test_status    VARCHAR(20)  DEFAULT NULL            COMMENT '最近测试状态',
    last_test_message   VARCHAR(255) DEFAULT NULL            COMMENT '最近测试结果描述',
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME     NOT NULL,
    CONSTRAINT uk_user_ai_config_user UNIQUE (user_id),
    CONSTRAINT fk_user_ai_config_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_ai_record (
    id              BIGINT    PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT    NOT NULL              COMMENT '操作用户',
    mail_id         BIGINT    NOT NULL              COMMENT '目标邮件',
    action_type     VARCHAR(30) NOT NULL            COMMENT '操作类型：SUMMARY / REPLY / TODO',
    prompt_snapshot TEXT                            COMMENT '发送给 AI 的提示词快照',
    result_json     LONGTEXT                        COMMENT 'AI 返回结果（JSON）',
    status          VARCHAR(20) NOT NULL            COMMENT '执行状态：SUCCESS / FAILED',
    error_message   VARCHAR(500) DEFAULT NULL       COMMENT '错误信息',
    created_at      DATETIME    NOT NULL,
    INDEX idx_mail_ai_record_user_mail  (user_id, mail_id),
    INDEX idx_mail_ai_record_action     (action_type),
    INDEX idx_mail_ai_record_created_at (created_at),
    CONSTRAINT fk_mail_ai_record_user FOREIGN KEY (user_id)  REFERENCES sys_user(id),
    CONSTRAINT fk_mail_ai_record_mail FOREIGN KEY (mail_id)  REFERENCES mail_message(id)
);

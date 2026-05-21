CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    introduction VARCHAR(255),
    auto_spam_filter TINYINT NOT NULL DEFAULT 0,
    auto_priority_filter TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS mail_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject VARCHAR(255) NOT NULL,
    content_html LONGTEXT NOT NULL,
    content_text TEXT,
    sender_user_id BIGINT NOT NULL,
    draft_flag TINYINT NOT NULL DEFAULT 0,
    has_attachment TINYINT NOT NULL DEFAULT 0,
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_mail_message_sender FOREIGN KEY (sender_user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_user_box (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mail_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    box_type VARCHAR(20) NOT NULL,
    role_type VARCHAR(20) NOT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    read_at DATETIME NULL,
    star_flag TINYINT NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    important_flag TINYINT NOT NULL DEFAULT 0,
    spam_flag TINYINT NOT NULL DEFAULT 0,
    spam_reason VARCHAR(500),
    spam_score DECIMAL(6, 4),
    spam_detected_at DATETIME,
    priority_score DOUBLE DEFAULT 0,
    priority_level VARCHAR(16),
    priority_reason VARCHAR(500),
    priority_scored_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_mail_user_box_owner(owner_user_id, box_type, deleted_flag),
    CONSTRAINT fk_mail_user_box_mail FOREIGN KEY (mail_id) REFERENCES mail_message(id),
    CONSTRAINT fk_mail_user_box_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_recipient (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mail_id BIGINT NOT NULL,
    recipient_user_id BIGINT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    recipient_email VARCHAR(100) NOT NULL,
    recipient_type VARCHAR(10) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_mail_recipient_mail(mail_id),
    CONSTRAINT fk_mail_recipient_mail FOREIGN KEY (mail_id) REFERENCES mail_message(id)
);

CREATE TABLE IF NOT EXISTS mail_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mail_id BIGINT NULL,
    owner_user_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT NOT NULL DEFAULT 0,
    file_hash VARCHAR(64),
    temp_flag TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    INDEX idx_mail_attachment_mail(mail_id),
    INDEX idx_mail_attachment_owner(owner_user_id),
    CONSTRAINT fk_mail_attachment_mail FOREIGN KEY (mail_id) REFERENCES mail_message(id),
    CONSTRAINT fk_mail_attachment_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_label (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_mail_label_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_user_label (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_box_id BIGINT NOT NULL,
    label_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_mail_user_label_box FOREIGN KEY (user_box_id) REFERENCES mail_user_box(id),
    CONSTRAINT fk_mail_user_label_label FOREIGN KEY (label_id) REFERENCES mail_label(id)
);

CREATE TABLE IF NOT EXISTS mail_contact_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    group_name VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_mail_contact_group_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    group_id BIGINT NULL,
    contact_user_id BIGINT NULL,
    contact_name VARCHAR(50) NOT NULL,
    contact_email VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(255),
    remark VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_mail_contact_owner(owner_user_id),
    CONSTRAINT fk_mail_contact_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_mail_contact_group FOREIGN KEY (group_id) REFERENCES mail_contact_group(id)
);

CREATE TABLE IF NOT EXISTS user_ai_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    api_key_encrypted VARCHAR(512) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    last_test_status VARCHAR(20) DEFAULT NULL,
    last_test_message VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_user_ai_config_user UNIQUE (user_id),
    CONSTRAINT fk_user_ai_config_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS mail_ai_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    mail_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    prompt_snapshot TEXT,
    result_json LONGTEXT,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500) DEFAULT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_mail_ai_record_user_mail (user_id, mail_id),
    INDEX idx_mail_ai_record_action (action_type),
    INDEX idx_mail_ai_record_created_at (created_at),
    CONSTRAINT fk_mail_ai_record_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_mail_ai_record_mail FOREIGN KEY (mail_id) REFERENCES mail_message(id)
);

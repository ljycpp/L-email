-- L邮箱 AI 模块增量迁移（在已有 mail_system 库上执行，可重复执行）
-- 用法：USE mail_system; SOURCE C:/mail-system-db/migration-ai.sql;

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

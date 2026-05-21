-- 智能垃圾邮件过滤：用户开关 + 识别原因（已存在列时请跳过对应语句）
ALTER TABLE sys_user
    ADD COLUMN auto_spam_filter TINYINT NOT NULL DEFAULT 0 COMMENT '1=开启智能垃圾邮件过滤';

ALTER TABLE mail_user_box
    ADD COLUMN spam_reason VARCHAR(500) NULL COMMENT '垃圾邮件识别原因',
    ADD COLUMN spam_score DECIMAL(6, 4) NULL COMMENT 'spam概率0-1',
    ADD COLUMN spam_detected_at DATETIME NULL COMMENT '自动识别时间';

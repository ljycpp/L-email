-- 智能邮件优先级：用户开关 + 评估结果（已存在列时请跳过对应语句）
ALTER TABLE sys_user
    ADD COLUMN auto_priority_filter TINYINT NOT NULL DEFAULT 0 COMMENT '1=开启智能邮件优先级';

ALTER TABLE mail_user_box
    ADD COLUMN priority_level VARCHAR(16) NULL COMMENT 'HIGH/MEDIUM/LOW',
    ADD COLUMN priority_reason VARCHAR(500) NULL COMMENT '优先级判定原因',
    ADD COLUMN priority_scored_at DATETIME NULL COMMENT '优先级评估时间';

-- 索引与唯一约束增量迁移（在已有 mail_system 库上执行）
-- 若索引/约束已存在，对应语句会报错，可跳过该条继续执行

ALTER TABLE mail_message ADD INDEX idx_mail_message_sender (sender_user_id);
ALTER TABLE mail_message ADD FULLTEXT INDEX idx_mail_message_search (subject, content_text);

ALTER TABLE mail_recipient ADD INDEX idx_mail_recipient_email (recipient_email);

ALTER TABLE mail_label ADD UNIQUE INDEX uk_mail_label_user_name (user_id, name);
ALTER TABLE mail_contact ADD UNIQUE INDEX uk_mail_contact_owner_email (owner_user_id, contact_email);
ALTER TABLE mail_contact_group ADD UNIQUE INDEX uk_mail_contact_group_owner_name (owner_user_id, group_name);
ALTER TABLE mail_user_label ADD UNIQUE INDEX uk_mail_user_label (user_box_id, label_id);

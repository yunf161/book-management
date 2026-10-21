-- 业务通知升级：保留人工消息，系统消息不伪造发送者；不建立外键。
ALTER TABLE message
    MODIFY sender_member_id BIGINT UNSIGNED NULL,
    ADD COLUMN message_type VARCHAR(16) NOT NULL DEFAULT 'CHAT',
    ADD COLUMN business_type VARCHAR(32) NULL,
    ADD COLUMN business_id BIGINT UNSIGNED NULL,
    ADD COLUMN notification_key VARCHAR(128) NULL,
    ADD UNIQUE KEY uk_message_notification (notification_key),
    ADD KEY idx_message_business (business_type,business_id),
    ADD CONSTRAINT chk_message_type CHECK (message_type IN ('CHAT','SYSTEM')),
    ADD CONSTRAINT chk_message_sender CHECK (
        (message_type='CHAT' AND sender_member_id IS NOT NULL AND notification_key IS NULL)
        OR (message_type='SYSTEM' AND sender_member_id IS NULL AND notification_key IS NOT NULL)
    );

INSERT INTO permission(code,name,module,description)
SELECT 'contact:query','通信联系人查询','COMMUNICATION','只返回允许联系的成员摘要'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='contact:query');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,CASE WHEN r.code='READER' THEN 'NONE' ELSE 'ALL' END
FROM `role` r JOIN permission p ON p.code='contact:query'
WHERE r.code IN ('READER','LIBRARIAN','ADMIN')
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

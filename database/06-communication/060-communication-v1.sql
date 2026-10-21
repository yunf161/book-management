-- 第一版通信迁移；索引创建只执行一次，不删除已有业务数据。
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    initiator_member_id BIGINT UNSIGNED NOT NULL,
    participant_one_id BIGINT UNSIGNED NOT NULL,
    participant_two_id BIGINT UNSIGNED NOT NULL,
    subject VARCHAR(200),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    last_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_message_id BIGINT UNSIGNED,
    last_message_at DATETIME(3),
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE (participant_one_id, participant_two_id),
    CHECK (status IN ('OPEN','CLOSED'))
);
CREATE INDEX idx_conversation_one_time ON conversation(participant_one_id,last_message_at,id);
CREATE INDEX idx_conversation_two_time ON conversation(participant_two_id,last_message_at,id);

CREATE TABLE IF NOT EXISTS message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED,
    sequence BIGINT UNSIGNED,
    sender_member_id BIGINT UNSIGNED NOT NULL,
    send_type VARCHAR(16) NOT NULL,
    subject VARCHAR(200),
    body TEXT NOT NULL,
    client_message_id VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    recipient_count INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE (sender_member_id,client_message_id),
    UNIQUE (conversation_id,sequence),
    CHECK (send_type IN ('DIRECT','MULTICAST'))
);
CREATE INDEX idx_message_sender_time ON message(sender_member_id,created_at,id);
CREATE INDEX idx_message_time ON message(created_at,id);

CREATE TABLE IF NOT EXISTS message_recipient (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT UNSIGNED NOT NULL,
    recipient_member_id BIGINT UNSIGNED NOT NULL,
    read_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE (message_id,recipient_member_id)
);
CREATE INDEX idx_recipient_inbox ON message_recipient(recipient_member_id,created_at,message_id);
CREATE INDEX idx_recipient_unread ON message_recipient(recipient_member_id,read_at,created_at,message_id);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id CHAR(36) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    schema_version INT UNSIGNED NOT NULL DEFAULT 1,
    message_id BIGINT UNSIGNED NOT NULL,
    recipient_member_id BIGINT UNSIGNED NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    lease_token CHAR(36),
    lease_until DATETIME(3),
    published_at DATETIME(3),
    last_error VARCHAR(1000),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE (event_type,message_id,recipient_member_id)
);
CREATE INDEX idx_outbox_pending ON outbox_event(status,next_attempt_at,id);
CREATE INDEX idx_outbox_lease ON outbox_event(status,lease_until,id);
INSERT INTO permission(code,name,module,description) SELECT 'conversation:create:self','conversation:create:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='conversation:create:self');
INSERT INTO permission(code,name,module,description) SELECT 'conversation:query:self','conversation:query:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='conversation:query:self');
INSERT INTO permission(code,name,module,description) SELECT 'conversation:update:self','conversation:update:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='conversation:update:self');
INSERT INTO permission(code,name,module,description) SELECT 'message:send:self','message:send:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='message:send:self');
INSERT INTO permission(code,name,module,description) SELECT 'message:query:self','message:query:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='message:query:self');
INSERT INTO permission(code,name,module,description) SELECT 'message:read:self','message:read:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='message:read:self');
INSERT INTO permission(code,name,module,description) SELECT 'websocket:connect:self','websocket:connect:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='websocket:connect:self');
INSERT INTO permission(code,name,module,description) SELECT 'message:send:multiple','message:send:multiple','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='message:send:multiple');
INSERT INTO role_permission(role_id,permission_id,data_scope) SELECT r.id,p.id,'SELF' FROM `role` r JOIN permission p ON p.module='COMMUNICATION' WHERE r.code IN ('READER','LIBRARIAN','ADMIN') AND (p.code <> 'message:send:multiple' OR r.code IN ('LIBRARIAN','ADMIN')) AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

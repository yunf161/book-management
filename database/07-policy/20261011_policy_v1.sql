-- 政策版本升级：不自动替任何成员接受新须知，不改变已有借阅到期时间。
CREATE TABLE IF NOT EXISTS policy_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    parameters_json LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    policy_version BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published_by BIGINT UNSIGNED NULL,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_policy_version (type, policy_version),
    KEY idx_policy_status (type, status),
    KEY idx_policy_publisher (published_by),
    CHECK (type IN ('ADMISSION','BORROWING')),
    CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS policy_current (
    type VARCHAR(16) PRIMARY KEY,
    policy_id BIGINT UNSIGNED NULL,
    sequence_value BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_current_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO policy_current(type) SELECT 'ADMISSION' WHERE NOT EXISTS (SELECT 1 FROM policy_current WHERE type='ADMISSION');
INSERT INTO policy_current(type) SELECT 'BORROWING' WHERE NOT EXISTS (SELECT 1 FROM policy_current WHERE type='BORROWING');
INSERT INTO policy_version(type,title,content,parameters_json,status,policy_version,published_at)
SELECT 'ADMISSION','读者准入政策','验证邮箱并接受当前借阅须知后，可以申请借阅资格。',
'{"requireVerifiedEmail":true,"requiredFields":["NAME"],"defaultMaxBorrowCount":5}',
'ACTIVE',1,CURRENT_TIMESTAMP(3) WHERE NOT EXISTS (SELECT 1 FROM policy_version WHERE type='ADMISSION');
INSERT INTO policy_version(type,title,content,parameters_json,status,policy_version,published_at)
SELECT 'BORROWING','图书借阅须知','借阅须经审批。请按期归还，逾期不得继续借阅。最多续借三次，逾期记录不能续借。',
'{"defaultBorrowDays":30,"maxBorrowDays":60,"maxRenewCount":3,"defaultRenewDays":30,"maxRenewDays":60,"blockBorrowWhenOverdue":true,"applicationValidityDays":7}',
'ACTIVE',1,CURRENT_TIMESTAMP(3) WHERE NOT EXISTS (SELECT 1 FROM policy_version WHERE type='BORROWING');
UPDATE policy_current c JOIN policy_version p ON p.type=c.type AND p.status='ACTIVE'
SET c.policy_id=p.id,c.sequence_value=p.policy_version WHERE c.policy_id IS NULL;

-- 下列结构变更由升级工具逐列检查后执行，重复执行时跳过已有列。
ALTER TABLE borrow_application ADD COLUMN borrowing_policy_id BIGINT UNSIGNED NULL;
ALTER TABLE borrow_application ADD COLUMN borrowing_policy_version BIGINT UNSIGNED NULL;
ALTER TABLE member_change_application ADD COLUMN admission_policy_id BIGINT UNSIGNED NULL;
ALTER TABLE member_change_application ADD COLUMN admission_policy_version BIGINT UNSIGNED NULL;
ALTER TABLE member_change_application ADD COLUMN borrowing_policy_id BIGINT UNSIGNED NULL;
ALTER TABLE member_change_application ADD COLUMN borrowing_policy_version BIGINT UNSIGNED NULL;
ALTER TABLE outbox_event MODIFY message_id BIGINT UNSIGNED NULL;
ALTER TABLE outbox_event MODIFY recipient_member_id BIGINT UNSIGNED NULL;


CREATE INDEX idx_borrow_policy ON borrow_application(borrowing_policy_id);
CREATE INDEX idx_change_admission_policy ON member_change_application(admission_policy_id);
CREATE INDEX idx_change_borrowing_policy ON member_change_application(borrowing_policy_id);
INSERT INTO permission(code,name,module,description) SELECT 'policy:query','policy:query','POLICY','Policy management' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='policy:query');
INSERT INTO permission(code,name,module,description) SELECT 'policy:create','policy:create','POLICY','Policy management' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='policy:create');
INSERT INTO permission(code,name,module,description) SELECT 'policy:update','policy:update','POLICY','Policy management' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='policy:update');
INSERT INTO permission(code,name,module,description) SELECT 'policy:publish','policy:publish','POLICY','Policy management' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='policy:publish');
INSERT INTO role_permission(role_id,permission_id,data_scope) SELECT r.id,p.id,'ALL' FROM role r JOIN permission p ON p.module='POLICY' WHERE r.code='ADMIN' AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

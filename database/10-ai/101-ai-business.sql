-- AI只存待确认命令与执行审计，业务数据仍由既有表和Service维护；不建立外键。
CREATE TABLE IF NOT EXISTS ai_business_action (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 action_id CHAR(36) NOT NULL,
 member_id BIGINT UNSIGNED NOT NULL,
 skill_code VARCHAR(64) NOT NULL,
 title VARCHAR(120) NOT NULL,
 request_json TEXT NOT NULL,
 request_hash CHAR(64) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PREPARED',
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 result_code INT DEFAULT NULL,
 result_json MEDIUMTEXT DEFAULT NULL,
 expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY (id),
 UNIQUE KEY uk_ai_action_id (action_id),
 KEY idx_ai_action_member_state (member_id,state,id),
 KEY idx_ai_action_member_hash (member_id,request_hash),
 KEY idx_ai_action_expires (expires_at),
 CONSTRAINT chk_ai_action_state CHECK (state IN ('PREPARED','EXECUTING','SUCCEEDED','FAILED','UNKNOWN','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI业务待确认操作及执行结果';

INSERT INTO permission(code,name,module,description,version)
SELECT 'ai:business:self','使用本人AI业务助手','ai','仅生成和确认本人的操作草稿，具体业务另行鉴权',0
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai:business:self');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'SELF' FROM `role` r CROSS JOIN permission p
WHERE r.code IN ('READER','LIBRARIAN','ADMIN') AND p.code='ai:business:self'
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

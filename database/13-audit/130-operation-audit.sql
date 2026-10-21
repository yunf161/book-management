-- 复用旧表并保留历史记录，可重复执行。无数据库外键。
CREATE TABLE IF NOT EXISTS member_operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    actor_kind VARCHAR(16) NOT NULL,
    actor_member_id BIGINT UNSIGNED NULL,
    actor_member_type VARCHAR(24) NULL,
    actor_role_id BIGINT UNSIGNED NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT UNSIGNED NULL,
    action_code VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    result_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    error_code VARCHAR(80) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_member_log_actor_time (actor_member_id,created_at,id),
    KEY idx_member_log_target_time (target_type,target_id,created_at,id),
    KEY idx_member_log_result_time (result_status,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一脱敏操作审计，应用只追加和查询';

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='member_operation_log' AND constraint_name='ck_member_operation_log_actor')>0, 'ALTER TABLE member_operation_log DROP CHECK ck_member_operation_log_actor', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='member_operation_log' AND constraint_name='ck_member_operation_log_target')>0, 'ALTER TABLE member_operation_log DROP CHECK ck_member_operation_log_target', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='member_operation_log' AND constraint_name='ck_member_operation_log_text')>0, 'ALTER TABLE member_operation_log DROP CHECK ck_member_operation_log_text', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_operation_log' AND column_name='actor_member_type')=0, 'ALTER TABLE member_operation_log ADD COLUMN actor_member_type VARCHAR(24) NULL', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_operation_log' AND column_name='actor_role_id')=0, 'ALTER TABLE member_operation_log ADD COLUMN actor_role_id BIGINT UNSIGNED NULL', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_operation_log' AND column_name='result_status')=0, 'ALTER TABLE member_operation_log ADD COLUMN result_status VARCHAR(16) NOT NULL DEFAULT ''SUCCESS''', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_operation_log' AND column_name='error_code')=0, 'ALTER TABLE member_operation_log ADD COLUMN error_code VARCHAR(80) NULL', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_operation_log' AND column_name='request_id')>0, 'ALTER TABLE member_operation_log CHANGE COLUMN request_id event_id VARCHAR(64) NOT NULL', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_operation_log' AND column_name='actor_level_snapshot')>0, 'UPDATE member_operation_log l LEFT JOIN role r ON r.code=CASE l.actor_level_snapshot WHEN 1 THEN ''READER'' WHEN 2 THEN ''LIBRARIAN'' WHEN 3 THEN ''ADMIN'' WHEN 4 THEN ''AI_LIBRARIAN'' END SET l.actor_member_type=CASE l.actor_level_snapshot WHEN 1 THEN ''READER'' WHEN 2 THEN ''EMPLOYEE'' WHEN 3 THEN ''EMPLOYEE'' WHEN 4 THEN ''AI_LIBRARIAN'' END, l.actor_role_id=r.id WHERE l.actor_kind=''MEMBER''', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_operation_log' AND column_name='actor_level_snapshot')>0, 'ALTER TABLE member_operation_log DROP COLUMN actor_level_snapshot', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

-- 失败的新增请求尚无资源主键，允许target_id为空。
ALTER TABLE member_operation_log MODIFY COLUMN target_id BIGINT UNSIGNED NULL;

SET @audit_sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='member_operation_log' AND index_name='idx_member_log_result_time')=0, 'CREATE INDEX idx_member_log_result_time ON member_operation_log(result_status,created_at,id)', 'SELECT 1');
PREPARE audit_statement FROM @audit_sql;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

INSERT INTO permission(code,name,module,description)
SELECT 'operation-log:query','查询操作审计','audit','查询全系统脱敏操作记录，需ALL数据范围'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='operation-log:query');

INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'ALL' FROM role r JOIN permission p ON p.code='operation-log:query'
WHERE r.code='ADMIN' AND NOT EXISTS (
    SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id
);


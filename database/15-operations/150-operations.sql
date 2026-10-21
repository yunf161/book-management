CREATE TABLE IF NOT EXISTS operation_job (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 job_id VARCHAR(36) NOT NULL UNIQUE,
 member_id BIGINT UNSIGNED NOT NULL,
 type VARCHAR(16) NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
 request_hash VARCHAR(64) NOT NULL UNIQUE,
 input_data MEDIUMBLOB,
 result_data MEDIUMBLOB,
 total_rows INT NOT NULL DEFAULT 0,
 completed_rows INT NOT NULL DEFAULT 0,
 success_rows INT NOT NULL DEFAULT 0,
 failed_rows INT NOT NULL DEFAULT 0,
 result_json TEXT,
 error_code VARCHAR(80),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_operation_job_member(member_id,id),
 KEY idx_operation_job_state(state,updated_at)
);
INSERT INTO permission(code,name,module,description) SELECT 'operations:query','查询运营统计','operations','ALL查看统计和本人/授权作业' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='operations:query');
INSERT INTO permission(code,name,module,description) SELECT 'operations:import','批量导入馆藏','operations','导入仍逐行调用原书目副本服务鉴权' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='operations:import');
INSERT INTO permission(code,name,module,description) SELECT 'operations:export','导出馆藏','operations','导出已授权的书目字段，不含人员敏感资料' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='operations:export');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'ALL' FROM role r CROSS JOIN permission p
WHERE r.code IN('LIBRARIAN','ADMIN') AND p.code IN('operations:query','operations:import','operations:export')
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

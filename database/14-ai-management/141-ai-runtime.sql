-- 一份任务只生成咨询/业务草稿，不自动执行业务写入，不保存JWT或内部凭据。
CREATE TABLE IF NOT EXISTS ai_task (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 task_id VARCHAR(36) NOT NULL UNIQUE,
 member_id BIGINT UNSIGNED NOT NULL,
 type VARCHAR(24) NOT NULL,
 question VARCHAR(2000) NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
 attempt INT NOT NULL DEFAULT 0,
 parent_task_id VARCHAR(36) DEFAULT NULL UNIQUE,
 result_json TEXT DEFAULT NULL,
 error_code VARCHAR(80) DEFAULT NULL,
 prompt_tokens BIGINT DEFAULT NULL,
 completion_tokens BIGINT DEFAULT NULL,
 estimated_cost DECIMAL(16,8) DEFAULT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_ai_task_member (member_id,id),
 KEY idx_ai_task_state (state,updated_at)
);
CREATE TABLE IF NOT EXISTS ai_runtime_control (
 code VARCHAR(32) NOT NULL PRIMARY KEY,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
INSERT INTO ai_runtime_control(code) SELECT 'GLOBAL' WHERE NOT EXISTS(SELECT 1 FROM ai_runtime_control WHERE code='GLOBAL');
INSERT INTO ai_runtime_control(code) SELECT 'CONSULTATION' WHERE NOT EXISTS(SELECT 1 FROM ai_runtime_control WHERE code='CONSULTATION');
INSERT INTO ai_runtime_control(code) SELECT 'BUSINESS' WHERE NOT EXISTS(SELECT 1 FROM ai_runtime_control WHERE code='BUSINESS');
INSERT INTO ai_runtime_control(code) SELECT 'RECOMMENDATION' WHERE NOT EXISTS(SELECT 1 FROM ai_runtime_control WHERE code='RECOMMENDATION');
INSERT INTO permission(code,name,module,description)
SELECT 'ai-task:query','查询AI任务','ai','SELF查看本人任务，ALL查看全局任务' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-task:query');
INSERT INTO permission(code,name,module,description)
SELECT 'ai-task:manage','管理AI任务','ai','SELF重试或取消本人任务，ALL可取消其他任务' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-task:manage');
INSERT INTO permission(code,name,module,description)
SELECT 'ai-control:manage','管理AI能力','ai','全局暂停和独立能力控制，要求ALL' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-control:manage');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,CASE WHEN r.code='ADMIN' THEN 'ALL' ELSE 'SELF' END
FROM role r CROSS JOIN permission p
WHERE ((r.code IN ('READER','LIBRARIAN','ADMIN') AND p.code IN('ai-task:query','ai-task:manage')) OR (r.code='ADMIN' AND p.code='ai-control:manage'))
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

CREATE TABLE IF NOT EXISTS ai_handoff (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT UNSIGNED NOT NULL,
 staff_member_id BIGINT UNSIGNED NOT NULL,
 conversation_id BIGINT UNSIGNED NOT NULL,
 ai_conversation_id VARCHAR(36) NOT NULL,
 summary VARCHAR(2000) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'WAITING',
 active_slot INT DEFAULT 1,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_ai_handoff_active(member_id,active_slot),
 KEY idx_ai_handoff_staff(staff_member_id,status,id),
 KEY idx_ai_handoff_conversation(conversation_id)
);
INSERT INTO permission(code,name,module,description) SELECT 'ai-handoff:create','请求AI转人工','ai','SELF本人申请，摘要由本人确认' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-handoff:create');
INSERT INTO permission(code,name,module,description) SELECT 'ai-handoff:query','查询转人工','ai','SELF本人或接收人，ALL全局' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-handoff:query');
INSERT INTO permission(code,name,module,description) SELECT 'ai-handoff:accept','接管AI咨询','ai','仅指定的在职馆员可接管' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-handoff:accept');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,CASE WHEN r.code='ADMIN' THEN 'ALL' ELSE 'SELF' END FROM role r CROSS JOIN permission p
WHERE ((r.code IN ('READER','LIBRARIAN','ADMIN') AND p.code IN('ai-handoff:create','ai-handoff:query')) OR (r.code IN ('LIBRARIAN','ADMIN') AND p.code='ai-handoff:accept'))
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

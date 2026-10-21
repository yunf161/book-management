-- 不新增业务表。通过code匹配角色和权限，不依赖固定ID；可重复执行。
INSERT INTO permission(code,name,module,description,version)
SELECT 'ai:chat:self','使用本人AI咨询','ai','只读咨询，会话归属于当前成员',0
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai:chat:self');

INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'SELF' FROM `role` r CROSS JOIN permission p
WHERE r.code IN ('READER','LIBRARIAN','ADMIN') AND p.code='ai:chat:self'
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

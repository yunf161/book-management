-- 知识内容保存在现有Python知识库目录/SQLite，MySQL只增加后台授权。
INSERT INTO permission(code,name,module,description)
SELECT 'knowledge:query','查询知识库','ai','查看知识文档、索引进度和检索测试，要求ALL'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='knowledge:query');
INSERT INTO permission(code,name,module,description)
SELECT 'knowledge:manage','维护知识文档','ai','维护受控知识正文与状态，要求ALL'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='knowledge:manage');
INSERT INTO permission(code,name,module,description)
SELECT 'knowledge:rebuild','重建知识索引','ai','创建异步索引任务，要求ALL'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='knowledge:rebuild');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'ALL' FROM role r CROSS JOIN permission p
WHERE r.code='ADMIN' AND p.code IN('knowledge:query','knowledge:manage','knowledge:rebuild')
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

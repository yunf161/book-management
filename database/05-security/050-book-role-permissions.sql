-- 图书模块权限定义和默认角色授权，可重复执行。
-- permission只描述操作；role_permission决定角色是否拥有权限及其数据范围。
INSERT INTO permission (code, name, module, description) VALUES
('book-category:query',  '查询图书分类', 'BOOK', '查询图书分类'),
('book-category:create', '新增图书分类', 'BOOK', '新增图书分类'),
('book-category:update', '修改图书分类', 'BOOK', '修改图书分类'),
('book-category:delete', '删除图书分类', 'BOOK', '删除图书分类'),
('book:query',           '查询图书',     'BOOK', '查询图书书目'),
('book:create',          '新增图书',     'BOOK', '新增图书书目'),
('book:update',          '修改图书',     'BOOK', '修改图书书目'),
('book:delete',          '删除图书',     'BOOK', '删除图书书目'),
('book-copy:query',      '查询图书副本', 'BOOK', '查询图书副本'),
('book-copy:create',     '新增图书副本', 'BOOK', '新增图书副本'),
('book-copy:update',     '修改图书副本', 'BOOK', '修改图书副本'),
('book-copy:delete',     '删除图书副本', 'BOOK', '删除图书副本')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    module = VALUES(module),
    description = VALUES(description);

-- 馆员和管理员拥有全部图书管理权限；这些操作不使用数据范围。
INSERT INTO role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'NONE'
FROM role r
JOIN permission p ON p.module = 'BOOK'
WHERE r.code IN ('LIBRARIAN', 'ADMIN')
ON DUPLICATE KEY UPDATE data_scope = VALUES(data_scope);

-- 读者和AI馆员只保留公开目录查询定义，不获得副本及写操作权限。
INSERT INTO role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'NONE'
FROM role r
JOIN permission p ON p.code IN ('book-category:query', 'book:query')
WHERE r.code IN ('READER', 'AI_LIBRARIAN')
ON DUPLICATE KEY UPDATE data_scope = VALUES(data_scope);

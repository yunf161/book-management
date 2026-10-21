-- 部门、成员和权限管理统一接入 role_permission 鉴权。
-- 角色和权限均通过 code 关联，不依赖固定自增 ID。

INSERT INTO permission (code, name, module, description)
VALUES
    ('member:query', '查询成员', 'IDENTITY', '查询成员及其领域资料'),
    ('member:create', '新增成员', 'IDENTITY', '新增成员及其领域资料'),
    ('member:update', '修改成员', 'IDENTITY', '修改成员及其领域资料'),
    ('member:delete', '删除成员', 'IDENTITY', '删除成员及其领域资料'),
    ('permission:query', '查询权限', 'SECURITY', '查询权限定义'),
    ('permission:create', '新增权限', 'SECURITY', '新增权限定义'),
    ('permission:update', '修改权限', 'SECURITY', '修改权限定义'),
    ('permission:delete', '删除权限', 'SECURITY', '删除未被角色引用的权限')
ON DUPLICATE KEY UPDATE code = VALUES(code);

-- 管理员拥有部门、成员和权限定义的全部管理能力。
INSERT INTO role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'ALL'
FROM role r
JOIN permission p ON p.code IN (
    'department:query', 'department:create', 'department:update', 'department:delete',
    'member:query', 'member:create', 'member:update', 'member:delete',
    'permission:query', 'permission:create', 'permission:update', 'permission:delete'
)
WHERE r.code = 'ADMIN'
ON DUPLICATE KEY UPDATE data_scope = VALUES(data_scope);

-- 馆员可查询部门和成员，并可修改自己的成员资料。
INSERT INTO role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id,
       CASE WHEN p.code = 'member:update' THEN 'SELF' ELSE 'ALL' END
FROM role r
JOIN permission p ON p.code IN ('department:query', 'member:query', 'member:update')
WHERE r.code = 'LIBRARIAN'
ON DUPLICATE KEY UPDATE data_scope = VALUES(data_scope);

-- 普通读者只能查询和修改自己的成员资料。
INSERT INTO role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'SELF'
FROM role r
JOIN permission p ON p.code IN ('member:query', 'member:update')
WHERE r.code = 'READER'
ON DUPLICATE KEY UPDATE data_scope = VALUES(data_scope);

-- AI Library 第一版角色权限升级脚本（MySQL 8）。
-- 执行前请备份 library 数据库；本脚本按当前线上表结构编写，仅执行一次。

SET NAMES utf8mb4;

-- 1. 创建角色表。
CREATE TABLE `role` (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code),
    UNIQUE KEY uk_role_name (name)
);

-- 2. 创建角色权限关联表，不建立数据库外键。
CREATE TABLE role_permission (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    data_scope    VARCHAR(16) NOT NULL DEFAULT 'NONE',
    version       BIGINT NOT NULL DEFAULT 0,
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_role_permission_role (role_id),
    KEY idx_role_permission_permission (permission_id)
);

-- 3. 插入默认角色，后续操作始终按角色编码查询主键。
INSERT INTO `role` (code, name, description)
VALUES
    ('READER', '读者', '普通读者角色'),
    ('LIBRARIAN', '馆员', '图书馆员工角色'),
    ('ADMIN', '管理员', '系统管理员角色'),
    ('AI_LIBRARIAN', 'AI馆员', 'AI馆员角色');

-- 4. 先以允许为空的形式增加 role_id，并建立普通索引。
ALTER TABLE member
    ADD COLUMN role_id BIGINT UNSIGNED NULL COMMENT '角色ID，对应role.id' AFTER permission_level,
    ADD KEY idx_member_role (role_id, id);

-- 5. 根据现有成员类型写入默认角色。
UPDATE member m
JOIN `role` r ON r.code = CASE m.permission_level
    WHEN 1 THEN 'READER'
    WHEN 2 THEN 'LIBRARIAN'
    WHEN 3 THEN 'ADMIN'
    WHEN 4 THEN 'AI_LIBRARIAN'
END
SET m.role_id = r.id;

-- 6. 此查询结果必须为 0；否则不要继续执行第 7 步。
SELECT COUNT(*) AS members_without_role
FROM member
WHERE role_id IS NULL;

-- 7. 将成员角色改为必填。
ALTER TABLE member
    MODIFY COLUMN role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID，对应role.id';

-- 使用旧 min_level 生成第一版人类角色授权。AI 馆员不参与等级继承。
INSERT INTO role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'NONE'
FROM `role` r
JOIN permission p ON
       (r.code = 'READER' AND p.min_level = 1)
    OR (r.code = 'LIBRARIAN' AND p.min_level IN (1, 2))
    OR (r.code = 'ADMIN' AND p.min_level IN (1, 2, 3));

-- 8. 删除 permission 的等级约束、等级索引和 min_level 字段。
ALTER TABLE permission
    DROP CHECK ck_permission_level,
    DROP INDEX idx_permission_level,
    DROP COLUMN min_level;

-- 9. 删除管理员唯一字段及其约束。
ALTER TABLE employee_profile
    DROP CHECK ck_employee_admin_key,
    DROP INDEX uk_employee_single_admin,
    DROP COLUMN admin_unique_key;

-- 10. 初始化部门权限定义。
INSERT IGNORE INTO permission (code, name, module, description)
VALUES
    ('department:query', '查询部门', 'IDENTITY', '查询部门信息'),
    ('department:create', '新增部门', 'IDENTITY', '新增部门信息'),
    ('department:update', '修改部门', 'IDENTITY', '修改或停用部门信息'),
    ('department:delete', '删除部门', 'IDENTITY', '删除无关联员工的部门');

-- 馆员可以查询部门；管理员拥有全部部门管理权限。AI 馆员不自动授权。
INSERT IGNORE INTO role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'NONE'
FROM `role` r
JOIN permission p ON p.code = 'department:query'
WHERE r.code = 'LIBRARIAN';

INSERT IGNORE INTO role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'NONE'
FROM `role` r
JOIN permission p ON p.code IN (
    'department:query',
    'department:create',
    'department:update',
    'department:delete'
)
WHERE r.code = 'ADMIN';


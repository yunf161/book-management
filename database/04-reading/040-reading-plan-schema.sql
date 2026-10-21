CREATE TABLE IF NOT EXISTS reading_plan (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, member_id BIGINT UNSIGNED NOT NULL,
 name VARCHAR(120) NOT NULL, description VARCHAR(1000), target_books INT UNSIGNED NOT NULL,
 start_date DATE, end_date DATE, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_reading_plan_member(member_id),
 KEY idx_reading_plan_status(status), KEY idx_reading_plan_updated(updated_at),
 CONSTRAINT chk_reading_plan_target CHECK(target_books > 0),
 CONSTRAINT chk_reading_plan_status CHECK(status IN('DRAFT','ACTIVE','PAUSED','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS reading_plan_item (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, plan_id BIGINT UNSIGNED NOT NULL,
 book_id BIGINT UNSIGNED NOT NULL, sequence_no INT UNSIGNED NOT NULL,
 priority INT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
 progress_percent INT UNSIGNED NOT NULL DEFAULT 0, source VARCHAR(16) NOT NULL DEFAULT 'USER',
 planned_start_date DATE, planned_finish_date DATE, started_at DATETIME(3), finished_at DATETIME(3),
 note VARCHAR(1000), version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_reading_plan_item_book(plan_id,book_id),
 UNIQUE KEY uk_reading_plan_item_sequence(plan_id,sequence_no),
 KEY idx_reading_plan_item_book(book_id), KEY idx_reading_plan_item_status(plan_id,status),
 CONSTRAINT chk_reading_item_sequence CHECK(sequence_no > 0),
 CONSTRAINT chk_reading_item_progress CHECK(progress_percent BETWEEN 0 AND 100),
 CONSTRAINT chk_reading_item_status CHECK(status IN('PLANNED','READING','COMPLETED','ABANDONED')),
 CONSTRAINT chk_reading_item_source CHECK(source IN('USER','LIBRARIAN','AI'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO permission(code,name,module,description) VALUES
 ('reading-plan:query:self','查询本人阅读计划','READING','查询本人阅读计划及条目'),
 ('reading-plan:create:self','新增本人阅读计划','READING','新增本人阅读计划及条目'),
 ('reading-plan:update:self','修改本人阅读计划','READING','修改本人阅读计划及条目'),
 ('reading-plan:delete:self','删除本人阅读计划','READING','删除本人阅读计划及条目'),
 ('reading-plan:query','查询阅读计划','READING','管理端查询阅读计划及条目'),
 ('reading-plan:create','新增阅读计划','READING','管理端新增阅读计划及条目'),
 ('reading-plan:update','修改阅读计划','READING','管理端修改阅读计划及条目'),
 ('reading-plan:delete','删除阅读计划','READING','管理端删除阅读计划及条目')
ON DUPLICATE KEY UPDATE module=VALUES(module),description=VALUES(description);
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'SELF' FROM role r JOIN permission p ON p.code LIKE 'reading-plan:%:self'
WHERE r.code IN('READER','LIBRARIAN','ADMIN')
ON DUPLICATE KEY UPDATE data_scope=VALUES(data_scope);
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'ALL' FROM role r JOIN permission p ON p.code IN
 ('reading-plan:query','reading-plan:create','reading-plan:update','reading-plan:delete')
WHERE r.code IN('LIBRARIAN','ADMIN')
ON DUPLICATE KEY UPDATE data_scope=VALUES(data_scope);

-- 管理端权限只授予馆员和管理员；清理旧版本可能误授予读者或AI馆员的关系。
DELETE rp
FROM role_permission rp
JOIN `role` r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE r.code IN ('READER', 'AI_LIBRARIAN')
  AND p.code IN (
    'reading-plan:query',
    'reading-plan:create',
    'reading-plan:update',
    'reading-plan:delete'
  );

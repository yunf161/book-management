-- 成员敏感字段变更申请。项目不建立物理外键，由Service在事务中校验关联。
-- 旧库的员工扩展表没有版本列；审批员工资料时使用该列完成乐观锁校验。
-- MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，使用元数据判断保证脚本可重复执行。
SET @add_employee_profile_version = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'employee_profile'
       AND column_name = 'version') = 0,
    'ALTER TABLE employee_profile ADD COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''员工扩展资料乐观锁版本''',
    'SELECT 1'
);
PREPARE add_employee_profile_version_stmt FROM @add_employee_profile_version;
EXECUTE add_employee_profile_version_stmt;
DEALLOCATE PREPARE add_employee_profile_version_stmt;

CREATE TABLE IF NOT EXISTS member_change_application
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_no           VARCHAR(32) NOT NULL,
    applicant_member_id      BIGINT UNSIGNED NOT NULL,
    target_member_id         BIGINT UNSIGNED NOT NULL,
    applicant_member_type    VARCHAR(20) NOT NULL,
    applicant_role_id        BIGINT UNSIGNED NOT NULL,
    application_type         VARCHAR(40) NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason                   VARCHAR(500) NOT NULL,
    expected_member_version  BIGINT UNSIGNED NOT NULL,
    expected_profile_version BIGINT UNSIGNED DEFAULT NULL,
    reviewer_member_id       BIGINT UNSIGNED DEFAULT NULL,
    reviewer_role_id         BIGINT UNSIGNED DEFAULT NULL,
    decision                 VARCHAR(16) DEFAULT NULL,
    decision_reason          VARCHAR(500) DEFAULT NULL,
    decided_at               DATETIME(3) DEFAULT NULL,
    cancelled_at             DATETIME(3) DEFAULT NULL,
    expires_at               DATETIME(3) DEFAULT NULL,
    version                  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                              ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_change_no (application_no),
    KEY idx_change_applicant (applicant_member_id, status, created_at),
    KEY idx_change_target (target_member_id, status, created_at),
    KEY idx_change_review (application_type, status, created_at),
    KEY idx_change_reviewer (reviewer_member_id, decided_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS reader_change_application
(
    application_id                  BIGINT UNSIGNED NOT NULL,
    current_qualification_status    VARCHAR(24) DEFAULT NULL,
    current_accepted_rule_version   VARCHAR(64) DEFAULT NULL,
    accepted_rule_version           VARCHAR(64) DEFAULT NULL,
    current_max_borrow_count        INT DEFAULT NULL,
    requested_max_borrow_count      INT DEFAULT NULL,
    quota_duration_days             INT DEFAULT NULL,
    current_account_status          VARCHAR(16) DEFAULT NULL,
    requested_account_action        VARCHAR(24) DEFAULT NULL,
    PRIMARY KEY (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 已执行过旧版脚本的数据库需要补充申请前规则版本快照。
SET @add_current_rule_version = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'reader_change_application'
       AND column_name = 'current_accepted_rule_version') = 0,
    'ALTER TABLE reader_change_application ADD COLUMN current_accepted_rule_version VARCHAR(64) DEFAULT NULL AFTER current_qualification_status',
    'SELECT 1'
);
PREPARE add_current_rule_version_stmt FROM @add_current_rule_version;
EXECUTE add_current_rule_version_stmt;
DEALLOCATE PREPARE add_current_rule_version_stmt;

CREATE TABLE IF NOT EXISTS employee_change_application
(
    application_id                  BIGINT UNSIGNED NOT NULL,
    current_department_id           BIGINT UNSIGNED DEFAULT NULL,
    requested_department_id         BIGINT UNSIGNED DEFAULT NULL,
    current_position_name           VARCHAR(80) DEFAULT NULL,
    requested_position_name         VARCHAR(80) DEFAULT NULL,
    current_employment_status       VARCHAR(20) DEFAULT NULL,
    requested_employment_action     VARCHAR(32) DEFAULT NULL,
    current_role_id                 BIGINT UNSIGNED DEFAULT NULL,
    requested_role_id               BIGINT UNSIGNED DEFAULT NULL,
    current_account_status          VARCHAR(16) DEFAULT NULL,
    requested_account_action        VARCHAR(24) DEFAULT NULL,
    current_hired_on                DATE DEFAULT NULL,
    current_left_on                 DATE DEFAULT NULL,
    PRIMARY KEY (application_id),
    KEY idx_employee_change_department (requested_department_id),
    KEY idx_employee_change_role (requested_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO permission(code,name,module,description)
SELECT 'member-change:create:self','提交本人变更申请','IDENTITY','提交本人敏感字段变更申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='member-change:create:self');
INSERT INTO permission(code,name,module,description)
SELECT 'member-change:query:self','查询本人变更申请','IDENTITY','查询本人提交的变更申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='member-change:query:self');
INSERT INTO permission(code,name,module,description)
SELECT 'member-change:cancel:self','取消本人变更申请','IDENTITY','取消本人待审批申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='member-change:cancel:self');
INSERT INTO permission(code,name,module,description)
SELECT 'member-change:query:reader','查询读者类变更申请','IDENTITY','查询读者资格和额度申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='member-change:query:reader');
INSERT INTO permission(code,name,module,description)
SELECT 'member-change:query:employee','查询员工类变更申请','IDENTITY','查询员工和管理员敏感变更申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='member-change:query:employee');
INSERT INTO permission(code,name,module,description)
SELECT 'reader-qualification:approve','审批读者资格','IDENTITY','审批读者资格申请和恢复申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='reader-qualification:approve');
INSERT INTO permission(code,name,module,description)
SELECT 'borrow-quota-change:approve','审批借阅额度','IDENTITY','审批借阅额度变更申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='borrow-quota-change:approve');
INSERT INTO permission(code,name,module,description)
SELECT 'member-account-change:approve','审批账号状态','IDENTITY','审批人类成员账号恢复申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='member-account-change:approve');
INSERT INTO permission(code,name,module,description)
SELECT 'employee-profile-change:approve','审批员工资料','IDENTITY','审批部门和岗位变更'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='employee-profile-change:approve');
INSERT INTO permission(code,name,module,description)
SELECT 'employee-status-change:approve','审批员工任职状态','IDENTITY','审批离职和复职申请'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='employee-status-change:approve');
INSERT INTO permission(code,name,module,description)
SELECT 'employee-role-change:approve','审批员工角色','IDENTITY','审批员工和管理员角色变更'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='employee-role-change:approve');

-- 本人申请能力授予所有人类角色。
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'SELF'
FROM `role` r JOIN permission p
  ON p.code IN ('member-change:create:self','member-change:query:self','member-change:cancel:self')
WHERE r.code IN ('READER','LIBRARIAN','ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );

-- 馆员和管理员可以处理读者资格及额度；员工类申请仅管理员可见和可批。
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'ALL'
FROM `role` r JOIN permission p
  ON p.code IN ('member-change:query:reader','reader-qualification:approve','borrow-quota-change:approve')
WHERE r.code IN ('LIBRARIAN','ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );

INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'ALL'
FROM `role` r JOIN permission p
  ON p.code IN (
    'member-change:query:employee','member-account-change:approve',
    'employee-profile-change:approve','employee-status-change:approve',
    'employee-role-change:approve'
  )
WHERE r.code='ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );

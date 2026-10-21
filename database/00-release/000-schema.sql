-- AI-library 1.0.0：只用于全新空库；现有library库不要执行。
SET NAMES utf8mb4;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键；对外使用字符串，Java使用Long',
  `code` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '部门编码，唯一、规范化大写；停用后不复用',
  `name` varchar(80) NOT NULL COMMENT '部门名称；首版平级部门',
  `description` varchar(500) DEFAULT NULL COMMENT '职责说明',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED；停用前处理仍任职员工',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '业务修改时显式加1；更新请求携带expectedVersion',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，连接时区固定UTC',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间；不是并发版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_code` (`code`),
  KEY `idx_department_status` (`status`,`id`),
  CONSTRAINT `ck_department_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED'))),
  CONSTRAINT `ck_department_text` CHECK (((char_length(trim(`code`)) > 0) and (char_length(trim(`name`)) > 0))),
  CONSTRAINT `ck_department_value` CHECK ((`version` >= 0))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门';
CREATE TABLE `role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(80) NOT NULL,
  `description` varchar(500) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`),
  UNIQUE KEY `uk_role_name` (`name`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `employee_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `member_id` bigint NOT NULL COMMENT '唯一逻辑关联member.id',
  `employee_no` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '唯一工号，服务端转大写',
  `department_id` bigint NOT NULL COMMENT '逻辑关联department.id',
  `position_name` varchar(80) NOT NULL COMMENT '岗位名称，不决定权限',
  `employment_status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/LEFT',
  `hired_on` date NOT NULL COMMENT '入职日期',
  `left_on` date DEFAULT NULL COMMENT '离职日期；LEFT时必填',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '员工扩展资料乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_member` (`member_id`),
  UNIQUE KEY `uk_employee_no` (`employee_no`),
  KEY `idx_employee_department_status` (`department_id`,`employment_status`,`id`),
  KEY `idx_employee_status` (`employment_status`,`id`),
  CONSTRAINT `ck_employee_dates` CHECK ((((`employment_status` = _ascii'LEFT') and (`left_on` is not null) and (`left_on` >= `hired_on`)) or ((`employment_status` in (_ascii'ACTIVE',_ascii'SUSPENDED')) and (`left_on` is null)))),
  CONSTRAINT `ck_employee_refs` CHECK (((`member_id` > 0) and (`department_id` > 0))),
  CONSTRAINT `ck_employee_status` CHECK ((`employment_status` in (_ascii'ACTIVE',_ascii'SUSPENDED',_ascii'LEFT'))),
  CONSTRAINT `ck_employee_text` CHECK (((char_length(trim(`employee_no`)) > 0) and (char_length(trim(`position_name`)) > 0)))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工工作资料';

CREATE TABLE `permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键；对外使用字符串，Java使用Long',
  `code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定权限编码，唯一，与后端授权声明对应',
  `name` varchar(80) NOT NULL COMMENT '权限名称',
  `module` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属业务模块编码',
  `description` varchar(500) NOT NULL COMMENT '权限含义及数据范围说明',
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，连接时区固定UTC',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间；不是并发版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`),
  CONSTRAINT `ck_permission_text` CHECK (((char_length(trim(`code`)) > 0) and (char_length(trim(`name`)) > 0)))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定人类权限';

CREATE TABLE `role_permission` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `role_id` bigint unsigned NOT NULL,
  `permission_id` bigint unsigned NOT NULL,
  `data_scope` varchar(16) NOT NULL DEFAULT 'NONE',
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_role_permission_role` (`role_id`),
  KEY `idx_role_permission_permission` (`permission_id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `name` varchar(80) NOT NULL COMMENT '全系统唯一展示名称；服务端先TRIM',
  `email` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs DEFAULT NULL COMMENT '人类必填且唯一；AI为NULL',
  `phone` varchar(32) DEFAULT NULL COMMENT '联系电话',
  `password_hash` varchar(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '人类必填；AI为NULL；禁止返回',
  `member_type` varchar(24) NOT NULL COMMENT '鎴愬憳棰嗗煙绫诲瀷',
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID，对应role.id',
  `account_status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `qualification_status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '借阅资格；AI为NOT_APPLICABLE',
  `email_verified_at` datetime(3) DEFAULT NULL COMMENT '邮箱验证时间；NULL表示未验证',
  `accepted_rule_version` varchar(40) DEFAULT NULL COMMENT '最近明确接受的借阅须知版本',
  `borrowed_book_count` int NOT NULL DEFAULT '0',
  `permanent_borrow_limit` int NOT NULL DEFAULT '5',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '人员及其附属资料共用的乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间，UTC',
  `temporary_borrow_limit` int DEFAULT NULL COMMENT '临时借阅总额度，仅在有效期内使用',
  `temporary_quota_expires_at` datetime(3) DEFAULT NULL COMMENT '临时额度到期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_name` (`name`),
  UNIQUE KEY `uk_member_email` (`email`),
  KEY `idx_member_qualification` (`qualification_status`,`id`),
  KEY `idx_member_role` (`role_id`,`id`),
  KEY `idx_member_type_status` (`member_type`,`account_status`,`id`),
  KEY `idx_member_temporary_quota_expiry` (`temporary_quota_expires_at`),
  CONSTRAINT `chk_member_borrow_count_nonnegative` CHECK ((`borrowed_book_count` >= 0)),
  CONSTRAINT `chk_member_permanent_limit_nonnegative` CHECK ((`permanent_borrow_limit` >= 0)),
  CONSTRAINT `chk_member_temporary_limit_nonnegative` CHECK (((`temporary_borrow_limit` is null) or (`temporary_borrow_limit` >= 0))),
  CONSTRAINT `chk_member_temporary_quota_pair` CHECK ((((`temporary_borrow_limit` is null) and (`temporary_quota_expires_at` is null)) or ((`temporary_borrow_limit` is not null) and (`temporary_quota_expires_at` is not null)))),
  CONSTRAINT `ck_member_account_status` CHECK ((`account_status` in (_ascii'ACTIVE',_ascii'DISABLED'))),
  CONSTRAINT `ck_member_borrowing_qualification` CHECK (((`qualification_status` not in (_ascii'APPROVED',_ascii'SUSPENDED')) or ((`email_verified_at` is not null) and (`accepted_rule_version` is not null)))),
  CONSTRAINT `ck_member_identity` CHECK (`member_type` IN ('READER','EMPLOYEE') AND `email` IS NOT NULL AND char_length(trim(`email`)) > 0 AND `password_hash` IS NOT NULL AND char_length(`password_hash`) > 0 AND `qualification_status` IN ('NOT_APPLIED','PENDING','APPROVED','REJECTED','SUSPENDED')),
  CONSTRAINT `ck_member_name` CHECK ((char_length(trim(`name`)) > 0)),
  CONSTRAINT `ck_member_qualification_status` CHECK ((`qualification_status` in (_ascii'NOT_APPLIED',_ascii'PENDING',_ascii'APPROVED',_ascii'REJECTED',_ascii'SUSPENDED',_ascii'NOT_APPLICABLE'))),
  CONSTRAINT `ck_member_rule_version` CHECK (((`accepted_rule_version` is null) or (char_length(trim(`accepted_rule_version`)) > 0))),
  CONSTRAINT `ck_member_type` CHECK ((`member_type` in (_utf8mb4'READER',_utf8mb4'EMPLOYEE'))),
  CONSTRAINT `ck_member_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一人员账号与借阅资格';


CREATE TABLE `reader_interest` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `member_id` bigint NOT NULL COMMENT '逻辑关联人类member.id',
  `interest_name` varchar(40) NOT NULL COMMENT '阅读兴趣名称；服务端TRIM并限制每人最多20项',
  `tag_id` bigint unsigned DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reader_interest_member_name` (`member_id`,`interest_name`),
  UNIQUE KEY `uk_reader_interest_tag` (`member_id`,`tag_id`),
  KEY `idx_reader_interest_member` (`member_id`,`id`),
  CONSTRAINT `ck_reader_interest_member` CHECK ((`member_id` > 0)),
  CONSTRAINT `ck_reader_interest_name` CHECK ((char_length(trim(`interest_name`)) > 0))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='读者阅读兴趣';

CREATE TABLE `reader_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `member_id` bigint NOT NULL COMMENT '唯一逻辑关联人类member.id',
  `personalization_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许行为个性化推荐，0否1是',
  `recommendation_notifications` tinyint NOT NULL DEFAULT '0' COMMENT '是否订阅推荐通知，0否1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reader_settings_member` (`member_id`),
  CONSTRAINT `ck_reader_settings_flags` CHECK (((`personalization_enabled` in (0,1)) and (`recommendation_notifications` in (0,1)))),
  CONSTRAINT `ck_reader_settings_member` CHECK ((`member_id` > 0))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='读者自主设置';

CREATE TABLE `member_change_application` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `application_no` varchar(32) NOT NULL,
  `applicant_member_id` bigint unsigned NOT NULL,
  `target_member_id` bigint unsigned NOT NULL,
  `applicant_member_type` varchar(20) NOT NULL,
  `applicant_role_id` bigint unsigned NOT NULL,
  `application_type` varchar(40) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `reason` varchar(500) NOT NULL,
  `expected_member_version` bigint unsigned NOT NULL,
  `expected_profile_version` bigint unsigned DEFAULT NULL,
  `reviewer_member_id` bigint unsigned DEFAULT NULL,
  `reviewer_role_id` bigint unsigned DEFAULT NULL,
  `decision` varchar(16) DEFAULT NULL,
  `decision_reason` varchar(500) DEFAULT NULL,
  `decided_at` datetime(3) DEFAULT NULL,
  `cancelled_at` datetime(3) DEFAULT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `admission_policy_id` bigint unsigned DEFAULT NULL,
  `admission_policy_version` bigint unsigned DEFAULT NULL,
  `borrowing_policy_id` bigint unsigned DEFAULT NULL,
  `borrowing_policy_version` bigint unsigned DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_change_no` (`application_no`),
  KEY `idx_change_applicant` (`applicant_member_id`,`status`,`created_at`),
  KEY `idx_change_target` (`target_member_id`,`status`,`created_at`),
  KEY `idx_change_review` (`application_type`,`status`,`created_at`),
  KEY `idx_change_reviewer` (`reviewer_member_id`,`decided_at`),
  KEY `idx_change_admission_policy` (`admission_policy_id`),
  KEY `idx_change_borrowing_policy` (`borrowing_policy_id`),
  KEY `idx_change_expiry` (`status`,`expires_at`,`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reader_change_application` (
  `application_id` bigint unsigned NOT NULL,
  `current_qualification_status` varchar(24) DEFAULT NULL,
  `current_accepted_rule_version` varchar(64) DEFAULT NULL,
  `accepted_rule_version` varchar(64) DEFAULT NULL,
  `current_max_borrow_count` int DEFAULT NULL,
  `requested_max_borrow_count` int DEFAULT NULL,
  `quota_duration_days` int DEFAULT NULL,
  `current_account_status` varchar(16) DEFAULT NULL,
  `requested_account_action` varchar(24) DEFAULT NULL,
  PRIMARY KEY (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `employee_change_application` (
  `application_id` bigint unsigned NOT NULL,
  `current_department_id` bigint unsigned DEFAULT NULL,
  `requested_department_id` bigint unsigned DEFAULT NULL,
  `current_position_name` varchar(80) DEFAULT NULL,
  `requested_position_name` varchar(80) DEFAULT NULL,
  `current_employment_status` varchar(20) DEFAULT NULL,
  `requested_employment_action` varchar(32) DEFAULT NULL,
  `current_role_id` bigint unsigned DEFAULT NULL,
  `requested_role_id` bigint unsigned DEFAULT NULL,
  `current_account_status` varchar(16) DEFAULT NULL,
  `requested_account_action` varchar(24) DEFAULT NULL,
  `current_hired_on` date DEFAULT NULL,
  `current_left_on` date DEFAULT NULL,
  PRIMARY KEY (`application_id`),
  KEY `idx_employee_change_department` (`requested_department_id`),
  KEY `idx_employee_change_role` (`requested_role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `book_category` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `code` varchar(32) NOT NULL COMMENT '分类编码',
  `name` varchar(80) NOT NULL COMMENT '分类名称',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '父分类ID，顶级分类为空',
  `description` varchar(500) DEFAULT NULL COMMENT '分类说明',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用、DISABLED停用',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_category_code` (`code`),
  KEY `idx_book_category_parent_id` (`parent_id`),
  KEY `idx_book_category_name` (`name`),
  KEY `idx_book_category_status` (`status`),
  CONSTRAINT `chk_book_category_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书分类表';

CREATE TABLE `book` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '图书ID',
  `category_id` bigint unsigned NOT NULL COMMENT '图书分类ID',
  `title` varchar(200) NOT NULL COMMENT '书名',
  `subtitle` varchar(200) DEFAULT NULL COMMENT '副标题',
  `author` varchar(200) NOT NULL COMMENT '作者',
  `isbn` varchar(20) DEFAULT NULL COMMENT 'ISBN，非空时唯一',
  `publisher` varchar(120) DEFAULT NULL COMMENT '出版社',
  `published_date` date DEFAULT NULL COMMENT '出版日期',
  `edition` varchar(40) DEFAULT NULL COMMENT '版本，例如第3版',
  `language` varchar(32) NOT NULL DEFAULT 'zh-CN' COMMENT '图书语言',
  `description` text COMMENT '图书简介',
  `cover_object_key` varchar(500) DEFAULT NULL COMMENT '阿里云OSS封面对象路径',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用、DISABLED停用',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_isbn` (`isbn`),
  KEY `idx_book_category_status` (`category_id`,`status`),
  KEY `idx_book_title` (`title`),
  KEY `idx_book_author` (`author`),
  KEY `idx_book_publisher` (`publisher`),
  KEY `idx_book_status` (`status`),
  CONSTRAINT `chk_book_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书书目表';

CREATE TABLE `book_copy` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '副本ID',
  `book_id` bigint unsigned NOT NULL COMMENT '所属图书ID',
  `barcode` varchar(64) NOT NULL COMMENT '馆藏条码',
  `call_number` varchar(64) DEFAULT NULL COMMENT '索书号',
  `location` varchar(120) NOT NULL COMMENT '馆藏位置',
  `status` varchar(24) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE、BORROWED、RESERVED、MAINTENANCE、LOST、DISCARDED',
  `acquired_at` date DEFAULT NULL COMMENT '入馆日期',
  `price` decimal(10,2) DEFAULT NULL COMMENT '采购价格',
  `condition_note` varchar(500) DEFAULT NULL COMMENT '实体状况说明',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_copy_barcode` (`barcode`),
  KEY `idx_book_copy_book_status` (`book_id`,`status`),
  KEY `idx_book_copy_call_number` (`call_number`),
  KEY `idx_book_copy_location` (`location`),
  KEY `idx_book_copy_status` (`status`),
  CONSTRAINT `chk_book_copy_price` CHECK (((`price` is null) or (`price` >= 0))),
  CONSTRAINT `chk_book_copy_status` CHECK ((`status` in (_utf8mb4'AVAILABLE',_utf8mb4'BORROWED',_utf8mb4'RESERVED',_utf8mb4'MAINTENANCE',_utf8mb4'LOST',_utf8mb4'DISCARDED')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书实体副本表';

CREATE TABLE `borrow_application` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '借阅申请ID',
  `application_type` varchar(16) NOT NULL DEFAULT 'BORROW' COMMENT '????????ORROW??ENEW',
  `member_id` bigint unsigned NOT NULL COMMENT '申请人member.id',
  `book_id` bigint unsigned NOT NULL COMMENT '申请借阅的book.id',
  `borrow_record_id` bigint unsigned DEFAULT NULL COMMENT '??????????????ID',
  `requested_days` int unsigned NOT NULL COMMENT '申请借阅天数',
  `reason` varchar(500) DEFAULT NULL COMMENT '申请说明',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、APPROVED、REJECTED、CANCELLED、EXPIRED',
  `submitted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
  `expires_at` datetime(3) DEFAULT NULL COMMENT '待审批申请过期时间',
  `processed_at` datetime(3) DEFAULT NULL COMMENT '审批处理时间',
  `processed_by` bigint unsigned DEFAULT NULL COMMENT '审批馆员member.id',
  `approved_copy_id` bigint unsigned DEFAULT NULL COMMENT '审批通过时分配的book_copy.id',
  `approved_days` int unsigned DEFAULT NULL COMMENT '实际批准借阅天数',
  `decision_remark` varchar(500) DEFAULT NULL COMMENT '审批意见或拒绝原因',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `borrowing_policy_id` bigint unsigned DEFAULT NULL,
  `borrowing_policy_version` bigint unsigned DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_borrow_application_member_status` (`member_id`,`status`),
  KEY `idx_borrow_application_book_status` (`book_id`,`status`),
  KEY `idx_borrow_application_status_submitted` (`status`,`submitted_at`),
  KEY `idx_borrow_application_expires` (`status`,`expires_at`),
  KEY `idx_borrow_application_processed_by` (`processed_by`),
  KEY `idx_borrow_application_approved_copy` (`approved_copy_id`),
  KEY `idx_borrow_application_record_status` (`borrow_record_id`,`status`),
  KEY `idx_borrow_application_queue` (`status`,`application_type`,`submitted_at`,`id`),
  KEY `idx_borrow_policy` (`borrowing_policy_id`),
  CONSTRAINT `chk_borrow_application_approved_days` CHECK (((`approved_days` is null) or (`approved_days` > 0))),
  CONSTRAINT `chk_borrow_application_requested_days` CHECK ((`requested_days` > 0)),
  CONSTRAINT `chk_borrow_application_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借阅申请表';

CREATE TABLE `borrow_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '借阅记录ID',
  `application_id` bigint unsigned NOT NULL COMMENT '来源borrow_application.id',
  `member_id` bigint unsigned NOT NULL COMMENT '借阅人member.id',
  `copy_id` bigint unsigned NOT NULL COMMENT '实际借出的book_copy.id',
  `borrowed_at` datetime(3) NOT NULL COMMENT '借出时间',
  `due_at` datetime(3) NOT NULL COMMENT '应归还时间',
  `returned_at` datetime(3) DEFAULT NULL COMMENT '实际归还时间',
  `status` varchar(24) NOT NULL DEFAULT 'BORROWING' COMMENT '状态：BORROWING、OVERDUE、RETURNED、LOST',
  `overdue_days` int unsigned NOT NULL DEFAULT '0' COMMENT '归还时确认的逾期天数',
  `approved_by` bigint unsigned NOT NULL COMMENT '批准借阅的馆员member.id',
  `returned_by` bigint unsigned DEFAULT NULL COMMENT '确认归还的馆员member.id',
  `return_condition_note` varchar(500) DEFAULT NULL COMMENT '归还时图书实体状况',
  `return_outcome` varchar(16) DEFAULT NULL COMMENT '结案结果：NORMAL、DAMAGED、LOST；未结案为空',
  `renew_count` int NOT NULL DEFAULT '0' COMMENT 'Number of approved renewals',
  `last_renewed_at` datetime(3) DEFAULT NULL COMMENT 'Time of the latest approved renewal',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_borrow_record_application` (`application_id`),
  KEY `idx_borrow_record_member_status` (`member_id`,`status`),
  KEY `idx_borrow_record_copy_status` (`copy_id`,`status`),
  KEY `idx_borrow_record_status_due` (`status`,`due_at`),
  KEY `idx_borrow_record_approved_by` (`approved_by`),
  KEY `idx_borrow_record_returned_by` (`returned_by`),
  CONSTRAINT `chk_borrow_record_due_at` CHECK ((`due_at` > `borrowed_at`)),
  CONSTRAINT `chk_borrow_record_overdue_days` CHECK ((`overdue_days` >= 0)),
  CONSTRAINT `chk_borrow_record_returned_at` CHECK (((`returned_at` is null) or (`returned_at` >= `borrowed_at`))),
  CONSTRAINT `chk_borrow_record_status` CHECK ((`status` in (_utf8mb4'BORROWING',_utf8mb4'OVERDUE',_utf8mb4'RETURNED',_utf8mb4'LOST')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借阅记录表';

CREATE TABLE `reading_plan` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '阅读计划ID',
  `member_id` bigint unsigned NOT NULL COMMENT '所属成员member.id，每个成员最多一份计划',
  `name` varchar(100) NOT NULL COMMENT '计划名称',
  `description` varchar(500) DEFAULT NULL COMMENT '计划说明',
  `target_books` int unsigned NOT NULL COMMENT '计划目标阅读数量',
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、ACTIVE、PAUSED、COMPLETED',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reading_plan_member` (`member_id`),
  KEY `idx_reading_plan_status` (`status`),
  KEY `idx_reading_plan_date` (`start_date`,`end_date`),
  CONSTRAINT `chk_reading_plan_dates` CHECK ((`end_date` >= `start_date`)),
  CONSTRAINT `chk_reading_plan_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'PAUSED',_utf8mb4'COMPLETED'))),
  CONSTRAINT `chk_reading_plan_target_books` CHECK ((`target_books` > 0))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成员阅读计划主表';

CREATE TABLE `reading_plan_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '阅读计划条目ID',
  `plan_id` bigint unsigned NOT NULL COMMENT '所属阅读计划reading_plan.id',
  `book_id` bigint unsigned NOT NULL COMMENT '计划阅读的图书book.id',
  `sequence_no` int unsigned NOT NULL COMMENT '在整个阅读计划中的顺序，从1开始',
  `priority` int unsigned NOT NULL DEFAULT '0' COMMENT '阅读优先级，数值越小优先级越高，0表示未单独设置',
  `status` varchar(24) NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED、READING、COMPLETED、ABANDONED',
  `planned_start_date` date DEFAULT NULL COMMENT '计划开始阅读日期',
  `planned_finish_date` date DEFAULT NULL COMMENT '计划完成阅读日期',
  `started_at` datetime(3) DEFAULT NULL COMMENT '实际开始阅读时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '实际完成阅读时间',
  `progress_percent` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '阅读进度，0至100',
  `source` varchar(16) NOT NULL DEFAULT 'USER' COMMENT '加入来源：USER、AI、LIBRARIAN',
  `note` varchar(1000) DEFAULT NULL COMMENT '阅读计划备注',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reading_plan_item_book` (`plan_id`,`book_id`),
  UNIQUE KEY `uk_reading_plan_item_sequence` (`plan_id`,`sequence_no`),
  KEY `idx_reading_plan_item_book` (`book_id`),
  KEY `idx_reading_plan_item_status` (`plan_id`,`status`),
  KEY `idx_reading_plan_item_priority` (`plan_id`,`priority`),
  CONSTRAINT `chk_reading_plan_item_actual_dates` CHECK (((`started_at` is null) or (`finished_at` is null) or (`finished_at` >= `started_at`))),
  CONSTRAINT `chk_reading_plan_item_dates` CHECK (((`planned_start_date` is null) or (`planned_finish_date` is null) or (`planned_finish_date` >= `planned_start_date`))),
  CONSTRAINT `chk_reading_plan_item_progress` CHECK (((`progress_percent` >= 0) and (`progress_percent` <= 100))),
  CONSTRAINT `chk_reading_plan_item_sequence` CHECK ((`sequence_no` > 0)),
  CONSTRAINT `chk_reading_plan_item_source` CHECK ((`source` in (_utf8mb4'USER',_utf8mb4'AI',_utf8mb4'LIBRARIAN'))),
  CONSTRAINT `chk_reading_plan_item_status` CHECK ((`status` in (_utf8mb4'PLANNED',_utf8mb4'READING',_utf8mb4'COMPLETED',_utf8mb4'ABANDONED')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阅读计划图书条目表';

CREATE TABLE `conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `initiator_member_id` bigint unsigned NOT NULL,
  `participant_one_id` bigint unsigned NOT NULL,
  `participant_two_id` bigint unsigned NOT NULL,
  `subject` varchar(200) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'OPEN',
  `last_sequence` bigint unsigned NOT NULL DEFAULT '0',
  `last_message_id` bigint unsigned DEFAULT NULL,
  `last_message_at` datetime(3) DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `participant_one_id` (`participant_one_id`,`participant_two_id`),
  KEY `idx_conversation_one_time` (`participant_one_id`,`last_message_at`,`id`),
  KEY `idx_conversation_two_time` (`participant_two_id`,`last_message_at`,`id`),
  CONSTRAINT `conversation_chk_1` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'CLOSED')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `sequence` bigint unsigned DEFAULT NULL,
  `sender_member_id` bigint unsigned DEFAULT NULL,
  `send_type` varchar(16) NOT NULL,
  `subject` varchar(200) DEFAULT NULL,
  `body` text NOT NULL,
  `client_message_id` varchar(64) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `recipient_count` int unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `message_type` varchar(16) NOT NULL DEFAULT 'CHAT',
  `business_type` varchar(32) DEFAULT NULL,
  `business_id` bigint unsigned DEFAULT NULL,
  `notification_key` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `sender_member_id` (`sender_member_id`,`client_message_id`),
  UNIQUE KEY `conversation_id` (`conversation_id`,`sequence`),
  UNIQUE KEY `uk_message_notification` (`notification_key`),
  KEY `idx_message_sender_time` (`sender_member_id`,`created_at`,`id`),
  KEY `idx_message_time` (`created_at`,`id`),
  KEY `idx_message_business` (`business_type`,`business_id`),
  CONSTRAINT `chk_message_sender` CHECK ((((`message_type` = _utf8mb4'CHAT') and (`sender_member_id` is not null) and (`notification_key` is null)) or ((`message_type` = _utf8mb4'SYSTEM') and (`sender_member_id` is null) and (`notification_key` is not null)))),
  CONSTRAINT `chk_message_type` CHECK ((`message_type` in (_utf8mb4'CHAT',_utf8mb4'SYSTEM'))),
  CONSTRAINT `message_chk_1` CHECK ((`send_type` in (_utf8mb4'DIRECT',_utf8mb4'MULTICAST')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `message_recipient` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `message_id` bigint unsigned NOT NULL,
  `recipient_member_id` bigint unsigned NOT NULL,
  `read_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `message_id` (`message_id`,`recipient_member_id`),
  KEY `idx_recipient_inbox` (`recipient_member_id`,`created_at`,`message_id`),
  KEY `idx_recipient_unread` (`recipient_member_id`,`read_at`,`created_at`,`message_id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `outbox_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_id` char(36) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `schema_version` int unsigned NOT NULL DEFAULT '1',
  `message_id` bigint unsigned DEFAULT NULL,
  `recipient_member_id` bigint unsigned DEFAULT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int unsigned NOT NULL DEFAULT '0',
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `lease_token` char(36) DEFAULT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_id` (`event_id`),
  UNIQUE KEY `event_type` (`event_type`,`message_id`,`recipient_member_id`),
  KEY `idx_outbox_pending` (`status`,`next_attempt_at`,`id`),
  KEY `idx_outbox_lease` (`status`,`lease_until`,`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `policy_version` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `type` varchar(16) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` longtext NOT NULL,
  `parameters_json` longtext NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `policy_version` bigint unsigned DEFAULT NULL,
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `published_by` bigint unsigned DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_policy_version` (`type`,`policy_version`),
  KEY `idx_policy_status` (`type`,`status`),
  KEY `idx_policy_publisher` (`published_by`),
  CONSTRAINT `policy_version_chk_1` CHECK ((`type` in (_utf8mb4'ADMISSION',_utf8mb4'BORROWING'))),
  CONSTRAINT `policy_version_chk_2` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'RETIRED')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `policy_current` (
  `type` varchar(16) NOT NULL,
  `policy_id` bigint unsigned DEFAULT NULL,
  `sequence_value` bigint unsigned NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`type`),
  KEY `idx_current_policy` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interest_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(40) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `book_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `book_id` bigint unsigned NOT NULL,
  `tag_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_tag` (`book_id`,`tag_id`),
  KEY `idx_book_tag_tag` (`tag_id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation_batch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `member_id` bigint unsigned NOT NULL,
  `request_id` varchar(64) NOT NULL,
  `source` varchar(16) NOT NULL DEFAULT 'RULE',
  `algorithm_version` varchar(32) NOT NULL,
  `status` varchar(16) NOT NULL,
  `generated_at` datetime(3) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recommendation_request` (`member_id`,`request_id`),
  KEY `idx_recommendation_current` (`member_id`,`status`,`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `batch_id` bigint unsigned NOT NULL,
  `book_id` bigint unsigned NOT NULL,
  `rank_order` int NOT NULL,
  `score` decimal(8,4) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `evidence_json` text NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recommendation_rank` (`batch_id`,`rank_order`),
  UNIQUE KEY `uk_recommendation_book` (`batch_id`,`book_id`),
  KEY `idx_recommendation_item_book` (`book_id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation_feedback` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `member_id` bigint unsigned NOT NULL,
  `batch_id` bigint unsigned NOT NULL,
  `item_id` bigint unsigned NOT NULL,
  `book_id` bigint unsigned NOT NULL,
  `kind` varchar(24) NOT NULL,
  `historical` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recommendation_feedback` (`member_id`,`item_id`,`kind`),
  KEY `idx_recommendation_feedback_history` (`member_id`,`historical`,`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_business_action` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `action_id` char(36) NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `skill_code` varchar(64) NOT NULL,
  `title` varchar(120) NOT NULL,
  `request_json` text NOT NULL,
  `request_hash` char(64) NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PREPARED',
  `version` bigint unsigned NOT NULL DEFAULT '0',
  `result_code` int DEFAULT NULL,
  `result_json` mediumtext,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_action_id` (`action_id`),
  KEY `idx_ai_action_member_state` (`member_id`,`state`,`id`),
  KEY `idx_ai_action_member_hash` (`member_id`,`request_hash`),
  KEY `idx_ai_action_expires` (`expires_at`),
  CONSTRAINT `chk_ai_action_state` CHECK ((`state` in (_utf8mb4'PREPARED',_utf8mb4'EXECUTING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'UNKNOWN',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI业务待确认操作及执行结果';

CREATE TABLE IF NOT EXISTS member_operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    actor_kind VARCHAR(16) NOT NULL,
    actor_member_id BIGINT UNSIGNED NULL,
    actor_member_type VARCHAR(24) NULL,
    actor_role_id BIGINT UNSIGNED NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT UNSIGNED NULL,
    action_code VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    result_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    error_code VARCHAR(80) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_member_log_actor_time (actor_member_id,created_at,id),
    KEY idx_member_log_target_time (target_type,target_id,created_at,id),
    KEY idx_member_log_result_time (result_status,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一脱敏操作审计，应用只追加和查询';

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

-- 仅保存模型返回的计量字段，不保存提示词或工具原文；唯一事件+序号避免重复记账。
CREATE TABLE IF NOT EXISTS ai_model_usage (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_id VARCHAR(36) NOT NULL,
 call_index INT NOT NULL,
 member_id BIGINT UNSIGNED NOT NULL,
 provider VARCHAR(16) NOT NULL,
 model VARCHAR(150) NOT NULL,
 prompt_tokens BIGINT NOT NULL,
 completion_tokens BIGINT NOT NULL,
 estimated_cost DECIMAL(16,8),
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_ai_usage_event(event_id,call_index),
 KEY idx_ai_usage_member(member_id,created_at)
);


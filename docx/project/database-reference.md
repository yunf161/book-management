# 数据表与字段索引

从当前新装DDL生成；逻辑关联、状态语义见系统设计，索引和CHECK约束请直接阅读SQL。不是生产数据库数据导出。

当前新装脚本包含 **36 张表**。

| 表 |
| --- |
| [department](#department) |
| [role](#role) |
| [employee_profile](#employee_profile) |
| [permission](#permission) |
| [role_permission](#role_permission) |
| [member](#member) |
| [reader_interest](#reader_interest) |
| [reader_settings](#reader_settings) |
| [member_change_application](#member_change_application) |
| [reader_change_application](#reader_change_application) |
| [employee_change_application](#employee_change_application) |
| [book_category](#book_category) |
| [book](#book) |
| [book_copy](#book_copy) |
| [borrow_application](#borrow_application) |
| [borrow_record](#borrow_record) |
| [reading_plan](#reading_plan) |
| [reading_plan_item](#reading_plan_item) |
| [conversation](#conversation) |
| [message](#message) |
| [message_recipient](#message_recipient) |
| [outbox_event](#outbox_event) |
| [policy_version](#policy_version) |
| [policy_current](#policy_current) |
| [interest_tag](#interest_tag) |
| [book_tag](#book_tag) |
| [recommendation_batch](#recommendation_batch) |
| [recommendation_item](#recommendation_item) |
| [recommendation_feedback](#recommendation_feedback) |
| [ai_business_action](#ai_business_action) |
| [member_operation_log](#member_operation_log) |
| [ai_task](#ai_task) |
| [ai_runtime_control](#ai_runtime_control) |
| [ai_handoff](#ai_handoff) |
| [operation_job](#operation_job) |
| [ai_model_usage](#ai_model_usage) |

## department

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键；对外使用字符串，Java使用Long' |
| code | varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '部门编码，唯一、规范化大写；停用后不复用' |
| name | varchar(80) NOT NULL COMMENT '部门名称；首版平级部门' |
| description | varchar(500) DEFAULT NULL COMMENT '职责说明' |
| status | varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED；停用前处理仍任职员工' |
| version | bigint NOT NULL DEFAULT '0' COMMENT '业务修改时显式加1；更新请求携带expectedVersion' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，连接时区固定UTC' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间；不是并发版本' |

## role

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| code | varchar(64) NOT NULL |
| name | varchar(80) NOT NULL |
| description | varchar(500) NOT NULL |
| status | varchar(16) NOT NULL DEFAULT 'ACTIVE' |
| version | bigint NOT NULL DEFAULT '0' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## employee_profile

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键' |
| member_id | bigint NOT NULL COMMENT '唯一逻辑关联member.id' |
| employee_no | varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '唯一工号，服务端转大写' |
| department_id | bigint NOT NULL COMMENT '逻辑关联department.id' |
| position_name | varchar(80) NOT NULL COMMENT '岗位名称，不决定权限' |
| employment_status | varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/LEFT' |
| hired_on | date NOT NULL COMMENT '入职日期' |
| left_on | date DEFAULT NULL COMMENT '离职日期；LEFT时必填' |
| version | bigint unsigned NOT NULL DEFAULT '0' COMMENT '员工扩展资料乐观锁版本' |

## permission

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键；对外使用字符串，Java使用Long' |
| code | varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定权限编码，唯一，与后端授权声明对应' |
| name | varchar(80) NOT NULL COMMENT '权限名称' |
| module | varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属业务模块编码' |
| description | varchar(500) NOT NULL COMMENT '权限含义及数据范围说明' |
| version | bigint NOT NULL DEFAULT '0' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，连接时区固定UTC' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间；不是并发版本' |

## role_permission

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| role_id | bigint unsigned NOT NULL |
| permission_id | bigint unsigned NOT NULL |
| data_scope | varchar(16) NOT NULL DEFAULT 'NONE' |
| version | bigint NOT NULL DEFAULT '0' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## member

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键' |
| name | varchar(80) NOT NULL COMMENT '全系统唯一展示名称；服务端先TRIM' |
| email | varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs DEFAULT NULL COMMENT '人类必填且唯一；AI为NULL' |
| phone | varchar(32) DEFAULT NULL COMMENT '联系电话' |
| password_hash | varchar(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '人类必填；AI为NULL；禁止返回' |
| member_type | varchar(24) NOT NULL COMMENT '鎴愬憳棰嗗煙绫诲瀷' |
| role_id | bigint unsigned NOT NULL COMMENT '角色ID，对应role.id' |
| account_status | varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED' |
| qualification_status | varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_APPLIED' COMMENT '借阅资格；AI为NOT_APPLICABLE' |
| email_verified_at | datetime(3) DEFAULT NULL COMMENT '邮箱验证时间；NULL表示未验证' |
| accepted_rule_version | varchar(40) DEFAULT NULL COMMENT '最近明确接受的借阅须知版本' |
| borrowed_book_count | int NOT NULL DEFAULT '0' |
| permanent_borrow_limit | int NOT NULL DEFAULT '5' |
| version | bigint NOT NULL DEFAULT '0' COMMENT '人员及其附属资料共用的乐观锁版本' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间，UTC' |
| temporary_borrow_limit | int DEFAULT NULL COMMENT '临时借阅总额度，仅在有效期内使用' |
| temporary_quota_expires_at | datetime(3) DEFAULT NULL COMMENT '临时额度到期时间' |

## reader_interest

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键' |
| member_id | bigint NOT NULL COMMENT '逻辑关联人类member.id' |
| interest_name | varchar(40) NOT NULL COMMENT '阅读兴趣名称；服务端TRIM并限制每人最多20项' |
| tag_id | bigint unsigned DEFAULT NULL |

## reader_settings

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键' |
| member_id | bigint NOT NULL COMMENT '唯一逻辑关联人类member.id' |
| personalization_enabled | tinyint NOT NULL DEFAULT '0' COMMENT '是否允许行为个性化推荐，0否1是' |
| recommendation_notifications | tinyint NOT NULL DEFAULT '0' COMMENT '是否订阅推荐通知，0否1是' |

## member_change_application

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| application_no | varchar(32) NOT NULL |
| applicant_member_id | bigint unsigned NOT NULL |
| target_member_id | bigint unsigned NOT NULL |
| applicant_member_type | varchar(20) NOT NULL |
| applicant_role_id | bigint unsigned NOT NULL |
| application_type | varchar(40) NOT NULL |
| status | varchar(20) NOT NULL DEFAULT 'PENDING' |
| reason | varchar(500) NOT NULL |
| expected_member_version | bigint unsigned NOT NULL |
| expected_profile_version | bigint unsigned DEFAULT NULL |
| reviewer_member_id | bigint unsigned DEFAULT NULL |
| reviewer_role_id | bigint unsigned DEFAULT NULL |
| decision | varchar(16) DEFAULT NULL |
| decision_reason | varchar(500) DEFAULT NULL |
| decided_at | datetime(3) DEFAULT NULL |
| cancelled_at | datetime(3) DEFAULT NULL |
| expires_at | datetime(3) DEFAULT NULL |
| version | bigint unsigned NOT NULL DEFAULT '0' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) |
| admission_policy_id | bigint unsigned DEFAULT NULL |
| admission_policy_version | bigint unsigned DEFAULT NULL |
| borrowing_policy_id | bigint unsigned DEFAULT NULL |
| borrowing_policy_version | bigint unsigned DEFAULT NULL |

## reader_change_application

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| application_id | bigint unsigned NOT NULL |
| current_qualification_status | varchar(24) DEFAULT NULL |
| current_accepted_rule_version | varchar(64) DEFAULT NULL |
| accepted_rule_version | varchar(64) DEFAULT NULL |
| current_max_borrow_count | int DEFAULT NULL |
| requested_max_borrow_count | int DEFAULT NULL |
| quota_duration_days | int DEFAULT NULL |
| current_account_status | varchar(16) DEFAULT NULL |
| requested_account_action | varchar(24) DEFAULT NULL |

## employee_change_application

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| application_id | bigint unsigned NOT NULL |
| current_department_id | bigint unsigned DEFAULT NULL |
| requested_department_id | bigint unsigned DEFAULT NULL |
| current_position_name | varchar(80) DEFAULT NULL |
| requested_position_name | varchar(80) DEFAULT NULL |
| current_employment_status | varchar(20) DEFAULT NULL |
| requested_employment_action | varchar(32) DEFAULT NULL |
| current_role_id | bigint unsigned DEFAULT NULL |
| requested_role_id | bigint unsigned DEFAULT NULL |
| current_account_status | varchar(16) DEFAULT NULL |
| requested_account_action | varchar(24) DEFAULT NULL |
| current_hired_on | date DEFAULT NULL |
| current_left_on | date DEFAULT NULL |

## book_category

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分类ID' |
| code | varchar(32) NOT NULL COMMENT '分类编码' |
| name | varchar(80) NOT NULL COMMENT '分类名称' |
| parent_id | bigint unsigned DEFAULT NULL COMMENT '父分类ID，顶级分类为空' |
| description | varchar(500) DEFAULT NULL COMMENT '分类说明' |
| status | varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用、DISABLED停用' |
| version | bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' |

## book

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '图书ID' |
| category_id | bigint unsigned NOT NULL COMMENT '图书分类ID' |
| title | varchar(200) NOT NULL COMMENT '书名' |
| subtitle | varchar(200) DEFAULT NULL COMMENT '副标题' |
| author | varchar(200) NOT NULL COMMENT '作者' |
| isbn | varchar(20) DEFAULT NULL COMMENT 'ISBN，非空时唯一' |
| publisher | varchar(120) DEFAULT NULL COMMENT '出版社' |
| published_date | date DEFAULT NULL COMMENT '出版日期' |
| edition | varchar(40) DEFAULT NULL COMMENT '版本，例如第3版' |
| language | varchar(32) NOT NULL DEFAULT 'zh-CN' COMMENT '图书语言' |
| description | text COMMENT '图书简介' |
| cover_object_key | varchar(500) DEFAULT NULL COMMENT '阿里云OSS封面对象路径' |
| status | varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用、DISABLED停用' |
| version | bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' |

## book_copy

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '副本ID' |
| book_id | bigint unsigned NOT NULL COMMENT '所属图书ID' |
| barcode | varchar(64) NOT NULL COMMENT '馆藏条码' |
| call_number | varchar(64) DEFAULT NULL COMMENT '索书号' |
| location | varchar(120) NOT NULL COMMENT '馆藏位置' |
| status | varchar(24) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE、BORROWED、RESERVED、MAINTENANCE、LOST、DISCARDED' |
| acquired_at | date DEFAULT NULL COMMENT '入馆日期' |
| price | decimal(10,2) DEFAULT NULL COMMENT '采购价格' |
| condition_note | varchar(500) DEFAULT NULL COMMENT '实体状况说明' |
| version | bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' |

## borrow_application

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '借阅申请ID' |
| application_type | varchar(16) NOT NULL DEFAULT 'BORROW' COMMENT '????????ORROW??ENEW' |
| member_id | bigint unsigned NOT NULL COMMENT '申请人member.id' |
| book_id | bigint unsigned NOT NULL COMMENT '申请借阅的book.id' |
| borrow_record_id | bigint unsigned DEFAULT NULL COMMENT '??????????????ID' |
| requested_days | int unsigned NOT NULL COMMENT '申请借阅天数' |
| reason | varchar(500) DEFAULT NULL COMMENT '申请说明' |
| status | varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、APPROVED、REJECTED、CANCELLED、EXPIRED' |
| submitted_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间' |
| expires_at | datetime(3) DEFAULT NULL COMMENT '待审批申请过期时间' |
| processed_at | datetime(3) DEFAULT NULL COMMENT '审批处理时间' |
| processed_by | bigint unsigned DEFAULT NULL COMMENT '审批馆员member.id' |
| approved_copy_id | bigint unsigned DEFAULT NULL COMMENT '审批通过时分配的book_copy.id' |
| approved_days | int unsigned DEFAULT NULL COMMENT '实际批准借阅天数' |
| decision_remark | varchar(500) DEFAULT NULL COMMENT '审批意见或拒绝原因' |
| version | bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' |
| borrowing_policy_id | bigint unsigned DEFAULT NULL |
| borrowing_policy_version | bigint unsigned DEFAULT NULL |

## borrow_record

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '借阅记录ID' |
| application_id | bigint unsigned NOT NULL COMMENT '来源borrow_application.id' |
| member_id | bigint unsigned NOT NULL COMMENT '借阅人member.id' |
| copy_id | bigint unsigned NOT NULL COMMENT '实际借出的book_copy.id' |
| borrowed_at | datetime(3) NOT NULL COMMENT '借出时间' |
| due_at | datetime(3) NOT NULL COMMENT '应归还时间' |
| returned_at | datetime(3) DEFAULT NULL COMMENT '实际归还时间' |
| status | varchar(24) NOT NULL DEFAULT 'BORROWING' COMMENT '状态：BORROWING、OVERDUE、RETURNED、LOST' |
| overdue_days | int unsigned NOT NULL DEFAULT '0' COMMENT '归还时确认的逾期天数' |
| approved_by | bigint unsigned NOT NULL COMMENT '批准借阅的馆员member.id' |
| returned_by | bigint unsigned DEFAULT NULL COMMENT '确认归还的馆员member.id' |
| return_condition_note | varchar(500) DEFAULT NULL COMMENT '归还时图书实体状况' |
| return_outcome | varchar(16) DEFAULT NULL COMMENT '结案结果：NORMAL、DAMAGED、LOST；未结案为空' |
| renew_count | int NOT NULL DEFAULT '0' COMMENT 'Number of approved renewals' |
| last_renewed_at | datetime(3) DEFAULT NULL COMMENT 'Time of the latest approved renewal' |
| version | bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' |

## reading_plan

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '阅读计划ID' |
| member_id | bigint unsigned NOT NULL COMMENT '所属成员member.id，每个成员最多一份计划' |
| name | varchar(100) NOT NULL COMMENT '计划名称' |
| description | varchar(500) DEFAULT NULL COMMENT '计划说明' |
| target_books | int unsigned NOT NULL COMMENT '计划目标阅读数量' |
| start_date | date DEFAULT NULL |
| end_date | date DEFAULT NULL |
| status | varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、ACTIVE、PAUSED、COMPLETED' |
| version | bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' |

## reading_plan_item

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '阅读计划条目ID' |
| plan_id | bigint unsigned NOT NULL COMMENT '所属阅读计划reading_plan.id' |
| book_id | bigint unsigned NOT NULL COMMENT '计划阅读的图书book.id' |
| sequence_no | int unsigned NOT NULL COMMENT '在整个阅读计划中的顺序，从1开始' |
| priority | int unsigned NOT NULL DEFAULT '0' COMMENT '阅读优先级，数值越小优先级越高，0表示未单独设置' |
| status | varchar(24) NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED、READING、COMPLETED、ABANDONED' |
| planned_start_date | date DEFAULT NULL COMMENT '计划开始阅读日期' |
| planned_finish_date | date DEFAULT NULL COMMENT '计划完成阅读日期' |
| started_at | datetime(3) DEFAULT NULL COMMENT '实际开始阅读时间' |
| finished_at | datetime(3) DEFAULT NULL COMMENT '实际完成阅读时间' |
| progress_percent | tinyint unsigned NOT NULL DEFAULT '0' COMMENT '阅读进度，0至100' |
| source | varchar(16) NOT NULL DEFAULT 'USER' COMMENT '加入来源：USER、AI、LIBRARIAN' |
| note | varchar(1000) DEFAULT NULL COMMENT '阅读计划备注' |
| version | bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' |

## conversation

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| initiator_member_id | bigint unsigned NOT NULL |
| participant_one_id | bigint unsigned NOT NULL |
| participant_two_id | bigint unsigned NOT NULL |
| subject | varchar(200) DEFAULT NULL |
| status | varchar(16) NOT NULL DEFAULT 'OPEN' |
| last_sequence | bigint unsigned NOT NULL DEFAULT '0' |
| last_message_id | bigint unsigned DEFAULT NULL |
| last_message_at | datetime(3) DEFAULT NULL |
| version | bigint unsigned NOT NULL DEFAULT '0' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## message

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| conversation_id | bigint unsigned DEFAULT NULL |
| sequence | bigint unsigned DEFAULT NULL |
| sender_member_id | bigint unsigned DEFAULT NULL |
| send_type | varchar(16) NOT NULL |
| subject | varchar(200) DEFAULT NULL |
| body | text NOT NULL |
| client_message_id | varchar(64) NOT NULL |
| request_hash | char(64) NOT NULL |
| recipient_count | int unsigned NOT NULL |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| message_type | varchar(16) NOT NULL DEFAULT 'CHAT' |
| business_type | varchar(32) DEFAULT NULL |
| business_id | bigint unsigned DEFAULT NULL |
| notification_key | varchar(128) DEFAULT NULL |

## message_recipient

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| message_id | bigint unsigned NOT NULL |
| recipient_member_id | bigint unsigned NOT NULL |
| read_at | datetime(3) DEFAULT NULL |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## outbox_event

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| event_id | char(36) NOT NULL |
| event_type | varchar(64) NOT NULL |
| schema_version | int unsigned NOT NULL DEFAULT '1' |
| message_id | bigint unsigned DEFAULT NULL |
| recipient_member_id | bigint unsigned DEFAULT NULL |
| payload_json | json NOT NULL |
| status | varchar(16) NOT NULL DEFAULT 'PENDING' |
| attempt_count | int unsigned NOT NULL DEFAULT '0' |
| next_attempt_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| lease_token | char(36) DEFAULT NULL |
| lease_until | datetime(3) DEFAULT NULL |
| published_at | datetime(3) DEFAULT NULL |
| last_error | varchar(1000) DEFAULT NULL |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## policy_version

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| type | varchar(16) NOT NULL |
| title | varchar(200) NOT NULL |
| content | longtext NOT NULL |
| parameters_json | longtext NOT NULL |
| status | varchar(16) NOT NULL DEFAULT 'DRAFT' |
| policy_version | bigint unsigned DEFAULT NULL |
| version | bigint unsigned NOT NULL DEFAULT '0' |
| published_by | bigint unsigned DEFAULT NULL |
| published_at | datetime(3) DEFAULT NULL |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## policy_current

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| type | varchar(16) NOT NULL |
| policy_id | bigint unsigned DEFAULT NULL |
| sequence_value | bigint unsigned NOT NULL DEFAULT '0' |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## interest_tag

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| code | varchar(64) NOT NULL |
| name | varchar(40) NOT NULL |
| status | varchar(16) NOT NULL DEFAULT 'ACTIVE' |
| version | bigint unsigned NOT NULL DEFAULT '0' |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## book_tag

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| book_id | bigint unsigned NOT NULL |
| tag_id | bigint unsigned NOT NULL |

## recommendation_batch

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| member_id | bigint unsigned NOT NULL |
| request_id | varchar(64) NOT NULL |
| source | varchar(16) NOT NULL DEFAULT 'RULE' |
| algorithm_version | varchar(32) NOT NULL |
| status | varchar(16) NOT NULL |
| generated_at | datetime(3) NOT NULL |
| expires_at | datetime(3) NOT NULL |

## recommendation_item

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| batch_id | bigint unsigned NOT NULL |
| book_id | bigint unsigned NOT NULL |
| rank_order | int NOT NULL |
| score | decimal(8,4) NOT NULL |
| reason | varchar(500) NOT NULL |
| evidence_json | text NOT NULL |

## recommendation_feedback

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| member_id | bigint unsigned NOT NULL |
| batch_id | bigint unsigned NOT NULL |
| item_id | bigint unsigned NOT NULL |
| book_id | bigint unsigned NOT NULL |
| kind | varchar(24) NOT NULL |
| historical | tinyint(1) NOT NULL DEFAULT '0' |
| created_at | datetime(3) NOT NULL |

## ai_business_action

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | bigint unsigned NOT NULL AUTO_INCREMENT |
| action_id | char(36) NOT NULL |
| member_id | bigint unsigned NOT NULL |
| skill_code | varchar(64) NOT NULL |
| title | varchar(120) NOT NULL |
| request_json | text NOT NULL |
| request_hash | char(64) NOT NULL |
| state | varchar(16) NOT NULL DEFAULT 'PREPARED' |
| version | bigint unsigned NOT NULL DEFAULT '0' |
| result_code | int DEFAULT NULL |
| result_json | mediumtext |
| expires_at | datetime(3) NOT NULL |
| created_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## member_operation_log

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | BIGINT UNSIGNED NOT NULL AUTO_INCREMENT |
| event_id | VARCHAR(64) NOT NULL |
| actor_kind | VARCHAR(16) NOT NULL |
| actor_member_id | BIGINT UNSIGNED NULL |
| actor_member_type | VARCHAR(24) NULL |
| actor_role_id | BIGINT UNSIGNED NULL |
| target_type | VARCHAR(32) NOT NULL |
| target_id | BIGINT UNSIGNED NULL |
| action_code | VARCHAR(64) NOT NULL |
| reason | VARCHAR(1000) NOT NULL |
| before_data | JSON NULL |
| after_data | JSON NULL |
| result_status | VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' |
| error_code | VARCHAR(80) NULL |
| created_at | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## ai_task

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY |
| task_id | VARCHAR(36) NOT NULL UNIQUE |
| member_id | BIGINT UNSIGNED NOT NULL |
| type | VARCHAR(24) NOT NULL |
| question | VARCHAR(2000) NOT NULL |
| state | VARCHAR(24) NOT NULL DEFAULT 'QUEUED' |
| attempt | INT NOT NULL DEFAULT 0 |
| parent_task_id | VARCHAR(36) DEFAULT NULL UNIQUE |
| result_json | TEXT DEFAULT NULL |
| error_code | VARCHAR(80) DEFAULT NULL |
| prompt_tokens | BIGINT DEFAULT NULL |
| completion_tokens | BIGINT DEFAULT NULL |
| estimated_cost | DECIMAL(16,8) DEFAULT NULL |
| version | BIGINT NOT NULL DEFAULT 0 |
| created_at | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## ai_runtime_control

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| code | VARCHAR(32) NOT NULL PRIMARY KEY |
| enabled | BOOLEAN NOT NULL DEFAULT TRUE |
| version | BIGINT NOT NULL DEFAULT 0 |
| updated_at | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## ai_handoff

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY |
| member_id | BIGINT UNSIGNED NOT NULL |
| staff_member_id | BIGINT UNSIGNED NOT NULL |
| conversation_id | BIGINT UNSIGNED NOT NULL |
| ai_conversation_id | VARCHAR(36) NOT NULL |
| summary | VARCHAR(2000) NOT NULL |
| status | VARCHAR(16) NOT NULL DEFAULT 'WAITING' |
| active_slot | INT DEFAULT 1 |
| version | BIGINT NOT NULL DEFAULT 0 |
| created_at | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## operation_job

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY |
| job_id | VARCHAR(36) NOT NULL UNIQUE |
| member_id | BIGINT UNSIGNED NOT NULL |
| type | VARCHAR(16) NOT NULL |
| state | VARCHAR(24) NOT NULL DEFAULT 'QUEUED' |
| request_hash | VARCHAR(64) NOT NULL UNIQUE |
| total_rows | INT NOT NULL DEFAULT 0 |
| completed_rows | INT NOT NULL DEFAULT 0 |
| success_rows | INT NOT NULL DEFAULT 0 |
| failed_rows | INT NOT NULL DEFAULT 0 |
| result_json | TEXT |
| error_code | VARCHAR(80) |
| created_at | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |
| updated_at | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) |

## ai_model_usage

| 字段 | SQL类型/声明摘要 |
| --- | --- |
| id | BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY |
| event_id | VARCHAR(36) NOT NULL |
| call_index | INT NOT NULL |
| member_id | BIGINT UNSIGNED NOT NULL |
| provider | VARCHAR(16) NOT NULL |
| model | VARCHAR(150) NOT NULL |
| prompt_tokens | BIGINT NOT NULL |
| completion_tokens | BIGINT NOT NULL |
| estimated_cost | DECIMAL(16,8) |
| created_at | DATETIME(3) NOT NULL |

完整结构：[000-schema.sql](../../database/00-release/000-schema.sql)。当前无AI成员档案表；AI任务、能力控制、人工交接、模型用量等属于服务模块，继续保留。

CREATE TABLE IF NOT EXISTS borrow_application (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, application_type VARCHAR(16) NOT NULL DEFAULT 'BORROW',
 member_id BIGINT UNSIGNED NOT NULL, book_id BIGINT UNSIGNED NOT NULL, borrow_record_id BIGINT UNSIGNED,
 requested_days INT NOT NULL, reason VARCHAR(500), status VARCHAR(16) NOT NULL, submitted_at DATETIME(3) NOT NULL,
 expires_at DATETIME(3) NOT NULL, processed_at DATETIME(3), processed_by BIGINT UNSIGNED, approved_copy_id BIGINT UNSIGNED,
 approved_days INT, decision_remark VARCHAR(500), version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_borrow_application_member_status(member_id,status), KEY idx_borrow_application_book_status(book_id,status),
 KEY idx_borrow_application_record_status(borrow_record_id,status), KEY idx_borrow_application_queue(status,application_type,submitted_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS borrow_record (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, application_id BIGINT UNSIGNED NOT NULL, member_id BIGINT UNSIGNED NOT NULL,
 copy_id BIGINT UNSIGNED NOT NULL, borrowed_at DATETIME(3) NOT NULL, due_at DATETIME(3) NOT NULL, returned_at DATETIME(3),
 status VARCHAR(16) NOT NULL, overdue_days INT NOT NULL DEFAULT 0, approved_by BIGINT UNSIGNED NOT NULL, returned_by BIGINT UNSIGNED,
 return_condition_note VARCHAR(500), renew_count INT NOT NULL DEFAULT 0, last_renewed_at DATETIME(3),
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_borrow_record_application(application_id), KEY idx_borrow_record_member_status(member_id,status), KEY idx_borrow_record_copy_status(copy_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO permission(code,name,module,description) VALUES
('borrow-application:create','提交借阅申请','BORROW','提交本人借阅申请'),('borrow-application:query','查询借阅申请','BORROW','查询借阅申请'),
('borrow-application:cancel','取消借阅申请','BORROW','取消本人待审批借阅申请'),
('borrow-application:approve','批准借阅申请','BORROW','批准借阅申请'),('borrow-application:reject','拒绝借阅申请','BORROW','拒绝借阅申请'),
('borrow-record:query','查询借阅记录','BORROW','查询借阅记录'),('borrow-record:return','归还图书','BORROW','办理图书归还');
INSERT IGNORE INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,CASE WHEN r.code IN ('LIBRARIAN','ADMIN') THEN 'ALL' ELSE 'SELF' END FROM role r JOIN permission p ON p.module='BORROW'
WHERE r.code IN ('READER','LIBRARIAN','ADMIN') AND (r.code!='READER' OR p.code IN ('borrow-application:create','borrow-application:query','borrow-application:cancel','borrow-record:query'));

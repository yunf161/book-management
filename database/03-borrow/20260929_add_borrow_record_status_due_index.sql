-- 为定时逾期同步和实时逾期查询补充联合索引；脚本可重复执行。
SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'borrow_record'
      AND index_name = 'idx_borrow_record_status_due'
);
SET @index_sql = IF(
    @index_exists = 0,
    'ALTER TABLE borrow_record ADD INDEX idx_borrow_record_status_due (status, due_at)',
    'SELECT 1'
);
PREPARE add_borrow_status_due_index FROM @index_sql;
EXECUTE add_borrow_status_due_index;
DEALLOCATE PREPARE add_borrow_status_due_index;

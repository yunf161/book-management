-- 已存在的RETURNED都是原正常归还流程生成，回填NORMAL；未结案记录仍为空。
-- 不根据可能已再次维护过的副本当前状态推断历史归还结果。
SET @return_outcome_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'borrow_record' AND column_name = 'return_outcome'
);
SET @return_outcome_sql = IF(@return_outcome_exists = 0,
    'ALTER TABLE borrow_record ADD COLUMN return_outcome VARCHAR(16) NULL COMMENT ''结案结果：NORMAL、DAMAGED、LOST；未结案为空'' AFTER return_condition_note',
    'SELECT 1');
PREPARE return_outcome_statement FROM @return_outcome_sql;
EXECUTE return_outcome_statement;
DEALLOCATE PREPARE return_outcome_statement;

UPDATE borrow_record SET return_outcome = 'NORMAL'
WHERE status = 'RETURNED' AND return_outcome IS NULL;
UPDATE borrow_record SET return_outcome = 'LOST'
WHERE status = 'LOST' AND return_outcome IS NULL;

-- 已有expires_at和EXPIRED，无需新增字段。只补充扫描索引；可重复执行。
SET @change_expiry_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'member_change_application'
      AND index_name = 'idx_change_expiry'
);
SET @change_expiry_index_sql = IF(
    @change_expiry_index_exists = 0,
    'CREATE INDEX idx_change_expiry ON member_change_application(status, expires_at, id)',
    'SELECT 1'
);
PREPARE change_expiry_index_statement FROM @change_expiry_index_sql;
EXECUTE change_expiry_index_statement;
DEALLOCATE PREPARE change_expiry_index_statement;

-- 人员长期额度改名；临时总额度独立保存，不覆盖长期值。
-- 通过 TemporaryQuotaMigration.java 执行以检查旧约束、已有列与索引，支持重复升级。
ALTER TABLE member RENAME COLUMN max_borrow_count TO permanent_borrow_limit;
ALTER TABLE member ADD CONSTRAINT chk_member_permanent_limit_nonnegative CHECK (permanent_borrow_limit >= 0);
ALTER TABLE member ADD COLUMN temporary_borrow_limit INT NULL COMMENT '临时借阅总额度，仅在有效期内使用';
ALTER TABLE member ADD COLUMN temporary_quota_expires_at DATETIME(3) NULL COMMENT '临时额度到期时间';
ALTER TABLE member ADD CONSTRAINT chk_member_temporary_limit_nonnegative CHECK (temporary_borrow_limit IS NULL OR temporary_borrow_limit >= 0);
ALTER TABLE member ADD CONSTRAINT chk_member_temporary_quota_pair CHECK ((temporary_borrow_limit IS NULL AND temporary_quota_expires_at IS NULL) OR (temporary_borrow_limit IS NOT NULL AND temporary_quota_expires_at IS NOT NULL));
CREATE INDEX idx_member_temporary_quota_expiry ON member(temporary_quota_expires_at);

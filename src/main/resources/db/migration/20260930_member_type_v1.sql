-- 将成员领域类型与角色权限彻底分离。
-- 执行前请先备份 member 表；本脚本不依赖任何固定角色 ID。

ALTER TABLE member
    ADD COLUMN member_type VARCHAR(24) NULL COMMENT '成员类型：READER、EMPLOYEE、AI_LIBRARIAN'
    AFTER permission_level;

UPDATE member
SET member_type = CASE permission_level
    WHEN 1 THEN 'READER'
    WHEN 2 THEN 'EMPLOYEE'
    WHEN 3 THEN 'EMPLOYEE'
    WHEN 4 THEN 'AI_LIBRARIAN'
END;

-- 未识别的旧类型必须先人工处理，下面的 NOT NULL 才能成功。
SELECT id, permission_level
FROM member
WHERE member_type IS NULL;

ALTER TABLE member
    DROP CHECK ck_member_identity,
    DROP CHECK ck_member_level,
    DROP INDEX idx_member_level_status,
    MODIFY COLUMN member_type VARCHAR(24) NOT NULL COMMENT '成员领域类型',
    ADD KEY idx_member_type_status (member_type, account_status, id),
    DROP COLUMN permission_level;

ALTER TABLE member
    ADD CONSTRAINT ck_member_type
        CHECK (member_type IN ('READER', 'EMPLOYEE', 'AI_LIBRARIAN')),
    ADD CONSTRAINT ck_member_identity
        CHECK (
            (
                member_type IN ('READER', 'EMPLOYEE')
                AND email IS NOT NULL
                AND CHAR_LENGTH(TRIM(email)) > 0
                AND password_hash IS NOT NULL
                AND CHAR_LENGTH(password_hash) > 0
                AND qualification_status IN (
                    'NOT_APPLIED', 'PENDING', 'APPROVED',
                    'REJECTED', 'SUSPENDED'
                )
            )
            OR
            (
                member_type = 'AI_LIBRARIAN'
                AND email IS NULL
                AND phone IS NULL
                AND password_hash IS NULL
                AND email_verified_at IS NULL
                AND accepted_rule_version IS NULL
                AND qualification_status = 'NOT_APPLICABLE'
            )
        );

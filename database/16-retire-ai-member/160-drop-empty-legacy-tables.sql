-- 第二阶段结构清理。先停止旧后端，并用 RetireAiMemberMigration 完成引用检查和本地备份。
-- 此脚本不删除人员数据；未完成迁移时拒绝删表。MySQL DDL 不是可回滚事务。
DELIMITER $$
DROP PROCEDURE IF EXISTS retire_empty_ai_member_tables$$
CREATE PROCEDURE retire_empty_ai_member_tables()
BEGIN
    IF EXISTS (SELECT 1 FROM member WHERE member_type = 'AI_LIBRARIAN') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Archive retired AI members first';
    END IF;
    IF EXISTS (SELECT 1 FROM `role` WHERE code = 'AI_LIBRARIAN') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Archive retired AI role first';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'ai_librarian_profile') THEN
        SET @legacy_count = NULL;
        PREPARE stmt FROM 'SELECT COUNT(*) INTO @legacy_count FROM ai_librarian_profile';
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        IF @legacy_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI profile table must be empty';
        END IF;
        DROP TABLE ai_librarian_profile;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'ai_capability') THEN
        SET @legacy_count = NULL;
        PREPARE stmt FROM 'SELECT COUNT(*) INTO @legacy_count FROM ai_capability';
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        IF @legacy_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy capability table must be empty';
        END IF;
        DROP TABLE ai_capability;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE() AND table_name = 'member'
                     AND constraint_name = 'ck_member_human_type') THEN
        ALTER TABLE member ADD CONSTRAINT ck_member_human_type
            CHECK (member_type IN ('READER', 'EMPLOYEE'));
    END IF;
END$$
CALL retire_empty_ai_member_tables()$$
DROP PROCEDURE retire_empty_ai_member_tables$$
DELIMITER ;

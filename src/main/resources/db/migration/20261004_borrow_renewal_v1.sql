-- Extend borrow applications with renewal requests and store renewal results.
-- Apply once after backing up the existing database.
-- The application uses transactions and resource locks instead of foreign keys.

ALTER TABLE borrow_application
    ADD COLUMN application_type VARCHAR(16) NOT NULL DEFAULT 'BORROW'
        COMMENT 'Application type: BORROW or RENEW' AFTER id,
    ADD COLUMN borrow_record_id BIGINT UNSIGNED NULL
        COMMENT 'Borrow record referenced by a renewal request' AFTER book_id,
    ADD INDEX idx_borrow_application_record_status (borrow_record_id, status),
    ADD INDEX idx_borrow_application_queue
        (status, application_type, submitted_at, id);

ALTER TABLE borrow_record
    ADD COLUMN renew_count INT NOT NULL DEFAULT 0
        COMMENT 'Number of approved renewals' AFTER return_condition_note,
    ADD COLUMN last_renewed_at DATETIME(3) NULL
        COMMENT 'Time of the latest approved renewal' AFTER renew_count;

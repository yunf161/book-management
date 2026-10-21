-- 借阅计数的数据库最终防线；执行前应先清理不符合条件的历史数据。
ALTER TABLE member
    ADD CONSTRAINT chk_member_borrow_count_nonnegative
        CHECK (borrowed_book_count >= 0),
    ADD CONSTRAINT chk_member_max_borrow_count_nonnegative
        CHECK (max_borrow_count >= 0),
    ADD CONSTRAINT chk_member_borrow_count_limit
        CHECK (borrowed_book_count <= max_borrow_count);

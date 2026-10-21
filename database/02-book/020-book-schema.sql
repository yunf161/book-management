CREATE TABLE IF NOT EXISTS book_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID', code VARCHAR(32) NOT NULL COMMENT '分类编码',
    name VARCHAR(80) NOT NULL COMMENT '分类名称', parent_id BIGINT UNSIGNED DEFAULT NULL COMMENT '父分类ID',
    description VARCHAR(500) DEFAULT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_book_category_code (code), KEY idx_book_category_parent_id (parent_id),
    KEY idx_book_category_name (name), KEY idx_book_category_status (status),
    CONSTRAINT chk_book_category_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书分类表';

CREATE TABLE IF NOT EXISTS book (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, category_id BIGINT UNSIGNED NOT NULL, title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(200), author VARCHAR(200) NOT NULL, isbn VARCHAR(20), publisher VARCHAR(120), published_date DATE,
    edition VARCHAR(40), language VARCHAR(32) NOT NULL DEFAULT 'zh-CN', description TEXT, cover_object_key VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_book_isbn (isbn), KEY idx_book_category_status (category_id,status),
    KEY idx_book_title (title), KEY idx_book_author (author), KEY idx_book_publisher (publisher), KEY idx_book_status (status),
    CONSTRAINT chk_book_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书书目表';

CREATE TABLE IF NOT EXISTS book_copy (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, book_id BIGINT UNSIGNED NOT NULL, barcode VARCHAR(64) NOT NULL,
    call_number VARCHAR(64), location VARCHAR(120) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    acquired_at DATE, price DECIMAL(10,2), condition_note VARCHAR(500), version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_book_copy_barcode (barcode), KEY idx_book_copy_book_status (book_id,status),
    KEY idx_book_copy_call_number (call_number), KEY idx_book_copy_location (location), KEY idx_book_copy_status (status),
    CONSTRAINT chk_book_copy_status CHECK (status IN ('AVAILABLE','BORROWED','RESERVED','MAINTENANCE','LOST','DISCARDED')),
    CONSTRAINT chk_book_copy_price CHECK (price IS NULL OR price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书实体副本表';

INSERT IGNORE INTO permission (code,name,module,description) VALUES
('book-category:query','查询图书分类','BOOK','查询图书分类'),('book-category:create','新增图书分类','BOOK','新增图书分类'),
('book-category:update','修改图书分类','BOOK','修改图书分类'),('book-category:delete','删除图书分类','BOOK','删除图书分类'),
('book:query','查询图书','BOOK','查询图书书目'),('book:create','新增图书','BOOK','新增图书书目'),
('book:update','修改图书','BOOK','修改图书书目'),('book:delete','删除图书','BOOK','删除图书书目'),
('book-copy:query','查询图书副本','BOOK','查询图书副本'),('book-copy:create','新增图书副本','BOOK','新增图书副本'),
('book-copy:update','修改图书副本','BOOK','修改图书副本'),('book-copy:delete','删除图书副本','BOOK','删除图书副本');

INSERT IGNORE INTO role_permission (role_id,permission_id,data_scope)
SELECT r.id,p.id,'NONE' FROM role r JOIN permission p ON p.module='BOOK'
WHERE r.code IN ('LIBRARIAN','ADMIN')
   OR (r.code IN ('READER','AI_LIBRARIAN') AND p.code IN ('book-category:query','book:query'));

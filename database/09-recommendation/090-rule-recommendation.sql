-- 标签规则推荐第一版。执行前备份；不创建外键，不修改既有成员类型和权限含义。
-- tag_id ADD COLUMN 仅首次升级执行，重复运行请跳过该 ALTER。
CREATE TABLE IF NOT EXISTS interest_tag (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code varchar(64) not null unique, name varchar(40) not null unique,
 status varchar(16) not null default 'ACTIVE', version BIGINT UNSIGNED not null default 0,
 created_at DATETIME(3) not null default CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) not null default CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS book_tag (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 book_id BIGINT UNSIGNED not null, tag_id BIGINT UNSIGNED not null,
 constraint uk_book_tag unique(book_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS recommendation_batch (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT UNSIGNED not null, request_id varchar(64) not null,
 source varchar(16) not null default 'RULE', algorithm_version varchar(32) not null,
 status varchar(16) not null, generated_at DATETIME(3) not null, expires_at DATETIME(3) not null,
 constraint uk_recommendation_request unique(member_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS recommendation_item (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 batch_id BIGINT UNSIGNED not null, book_id BIGINT UNSIGNED not null, rank_order integer not null,
 score decimal(8,4) not null, reason varchar(500) not null, evidence_json text not null,
 constraint uk_recommendation_rank unique(batch_id, rank_order),
 constraint uk_recommendation_book unique(batch_id, book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS recommendation_feedback (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT UNSIGNED not null, batch_id BIGINT UNSIGNED not null, item_id BIGINT UNSIGNED not null, book_id BIGINT UNSIGNED not null,
 kind varchar(24) not null, historical boolean not null default false,
 created_at DATETIME(3) not null,
 constraint uk_recommendation_feedback unique(member_id, item_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_book_tag_tag on book_tag(tag_id);
CREATE INDEX idx_recommendation_current on recommendation_batch(member_id, status, id);
CREATE INDEX idx_recommendation_item_book on recommendation_item(book_id);
CREATE INDEX idx_recommendation_feedback_history on recommendation_feedback(member_id, historical, id);

INSERT IGNORE INTO interest_tag (code,name) values
 ('JAVA','Java'),('BACKEND','后端开发'),('DATABASE','数据库'),
 ('LITERATURE','文学'),('HISTORY','历史'),('SCIENCE','科学'),('ARTIFICIAL_INTELLIGENCE','人工智能');

ALTER TABLE reader_interest ADD COLUMN tag_id BIGINT UNSIGNED NULL;
-- 将旧的自由文本兴趣迁移到字典，名称保留供原人员接口兼容显示。
INSERT IGNORE INTO interest_tag (code,name)
 SELECT CONCAT('LEGACY_',UPPER(SUBSTRING(SHA2(TRIM(interest_name),256),1,16))),TRIM(interest_name)
 FROM reader_interest WHERE TRIM(interest_name) != '';
UPDATE reader_interest ri JOIN interest_tag t ON t.name = TRIM(ri.interest_name)
 SET ri.tag_id = t.id;
DELETE ri FROM reader_interest ri JOIN reader_interest keep_row
 ON ri.member_id = keep_row.member_id AND ri.tag_id = keep_row.tag_id AND ri.id > keep_row.id;
CREATE UNIQUE INDEX uk_reader_interest_tag ON reader_interest(member_id,tag_id);

INSERT IGNORE INTO permission (code,name,module,description) values
 ('recommendation:query:self','查询本人推荐','recommendation','仅当前批次'),
 ('recommendation:refresh:self','刷新本人推荐','recommendation','每次最多六本'),
 ('recommendation:feedback:self','提交推荐反馈','recommendation','本人当前推荐条目'),
 ('interests:update:self','修改本人兴趣','recommendation','标签全量替换');
INSERT IGNORE INTO role_permission(role_id,permission_id,data_scope)
 select r.id,p.id,'SELF' from `role` r cross join permission p
 where r.code in ('READER','LIBRARIAN','ADMIN')
 and p.code in ('recommendation:query:self','recommendation:refresh:self','recommendation:feedback:self','interests:update:self');

-- 初始书目标签通过现有分类编码关联，不依赖自增 ID。
INSERT IGNORE INTO book_tag(book_id,tag_id)
 SELECT b.id,t.id FROM book b JOIN book_category c ON c.id=b.category_id
 JOIN interest_tag t ON t.code=CASE c.code
 WHEN 'JAVA' THEN 'JAVA' WHEN 'DATABASE' THEN 'DATABASE' WHEN 'LITERATURE' THEN 'LITERATURE'
 WHEN 'HISTORY' THEN 'HISTORY' WHEN 'SCIENCE' THEN 'SCIENCE' WHEN 'AI' THEN 'ARTIFICIAL_INTELLIGENCE' END;

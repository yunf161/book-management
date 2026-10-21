INSERT IGNORE INTO book_category(code,name,parent_id,description) VALUES
('COMPUTER','计算机',NULL,'计算机技术'),('LITERATURE','文学',NULL,'文学作品'),
('HISTORY','历史',NULL,'历史读物'),('SCIENCE','科学',NULL,'科学读物');
INSERT IGNORE INTO book_category(code,name,parent_id,description)
SELECT 'JAVA','Java',id,'Java技术' FROM book_category WHERE code='COMPUTER';
INSERT IGNORE INTO book_category(code,name,parent_id,description)
SELECT 'DATABASE','数据库',id,'数据库技术' FROM book_category WHERE code='COMPUTER';
INSERT IGNORE INTO book_category(code,name,parent_id,description)
SELECT 'AI','人工智能',id,'人工智能技术' FROM book_category WHERE code='COMPUTER';

INSERT IGNORE INTO book(category_id,title,author,isbn,publisher,language)
SELECT id,'Java核心技术','Cay S. Horstmann','9787111636663','机械工业出版社','zh-CN' FROM book_category WHERE code='JAVA';
INSERT IGNORE INTO book(category_id,title,author,isbn,publisher,language)
SELECT id,'深入理解Java虚拟机','周志明','9787111641247','机械工业出版社','zh-CN' FROM book_category WHERE code='JAVA';
INSERT IGNORE INTO book(category_id,title,author,isbn,publisher,language)
SELECT id,'高性能MySQL','Baron Schwartz','9787121198854','电子工业出版社','zh-CN' FROM book_category WHERE code='DATABASE';
INSERT IGNORE INTO book(category_id,title,author,isbn,publisher,language)
SELECT id,'深度学习','Ian Goodfellow','9787115461476','人民邮电出版社','zh-CN' FROM book_category WHERE code='AI';
INSERT IGNORE INTO book(category_id,title,author,isbn,publisher,language)
SELECT id,'活着','余华','9787506365437','作家出版社','zh-CN' FROM book_category WHERE code='LITERATURE';

INSERT IGNORE INTO book_copy(book_id,barcode,call_number,location,status)
SELECT id,'BC-JAVA-001','TP312-J1','A区-01','AVAILABLE' FROM book WHERE isbn='9787111636663';
INSERT IGNORE INTO book_copy(book_id,barcode,call_number,location,status)
SELECT id,'BC-JAVA-002','TP312-J1','A区-01','AVAILABLE' FROM book WHERE isbn='9787111636663';
INSERT IGNORE INTO book_copy(book_id,barcode,call_number,location,status)
SELECT id,'BC-JVM-001','TP312-J2','A区-02','MAINTENANCE' FROM book WHERE isbn='9787111641247';
INSERT IGNORE INTO book_copy(book_id,barcode,call_number,location,status)
SELECT id,'BC-MYSQL-001','TP311-D1','A区-03','AVAILABLE' FROM book WHERE isbn='9787121198854';

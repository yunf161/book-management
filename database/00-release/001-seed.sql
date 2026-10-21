-- 公共角色、权限、政策与兴趣字典；不包含成员、密码或个人业务数据。
SET NAMES utf8mb4;
insert into `role` (code, name, description) values
    ('READER', '读者', '普通读者角色'),
    ('LIBRARIAN', '馆员', '图书馆员工角色'),
    ('ADMIN', '管理员', '系统管理员角色');

insert into permission (code, name, module, description) values
    ('department:query', '查询部门', 'IDENTITY', '查询部门信息'),
    ('department:create', '新增部门', 'IDENTITY', '新增部门信息'),
    ('department:update', '修改部门', 'IDENTITY', '修改或停用部门信息'),
    ('department:delete', '删除部门', 'IDENTITY', '删除无关联员工的部门'),
    ('member:query', '查询成员', 'IDENTITY', '查询成员资料'),
    ('member:create', '新增成员', 'IDENTITY', '新增成员资料'),
    ('member:update', '修改成员', 'IDENTITY', '修改成员资料'),
    ('member:delete', '删除成员', 'IDENTITY', '删除成员资料'),
    ('permission:query', '查询权限', 'SECURITY', '查询权限定义'),
    ('permission:create', '新增权限', 'SECURITY', '新增权限定义'),
    ('permission:update', '修改权限', 'SECURITY', '修改权限定义'),
    ('permission:delete', '删除权限', 'SECURITY', '删除权限定义');

insert into role_permission (role_id, permission_id, data_scope)
select r.id, p.id, 'NONE'
from `role` r
join permission p on p.code = 'department:query'
where r.code = 'LIBRARIAN';

insert into role_permission (role_id, permission_id, data_scope)
select r.id, p.id, 'NONE'
from `role` r
join permission p on p.code in (
    'department:query',
    'department:create',
    'department:update',
    'department:delete'
)
where r.code = 'ADMIN';

insert into role_permission (role_id, permission_id, data_scope)
select r.id, p.id, 'ALL'
from `role` r
join permission p on p.code in (
    'member:query', 'member:create', 'member:update', 'member:delete',
    'permission:query', 'permission:create', 'permission:update', 'permission:delete'
)
where r.code = 'ADMIN';

insert into role_permission (role_id, permission_id, data_scope)
select r.id, p.id, case when p.code = 'member:update' then 'SELF' else 'ALL' end
from `role` r
join permission p on p.code in ('member:query', 'member:update')
where r.code = 'LIBRARIAN';

insert into role_permission (role_id, permission_id, data_scope)
select r.id, p.id, 'SELF'
from `role` r
join permission p on p.code in ('member:query', 'member:update')
where r.code = 'READER';

insert into permission(code,name,module,description) values
 ('member-change:create:self','提交本人变更申请','IDENTITY','提交本人敏感字段变更申请'),
 ('member-change:query:self','查询本人变更申请','IDENTITY','查询本人提交的变更申请'),
 ('member-change:cancel:self','取消本人变更申请','IDENTITY','取消本人待审批申请'),
 ('member-change:query:reader','查询读者类变更申请','IDENTITY','查询读者资格和额度申请'),
 ('member-change:query:employee','查询员工类变更申请','IDENTITY','查询员工和管理员敏感变更申请'),
 ('reader-qualification:approve','审批读者资格','IDENTITY','审批读者资格申请和恢复申请'),
 ('borrow-quota-change:approve','审批借阅额度','IDENTITY','审批借阅额度变更申请'),
 ('member-account-change:approve','审批账号状态','IDENTITY','审批人类成员账号恢复申请'),
 ('employee-profile-change:approve','审批员工资料','IDENTITY','审批部门和岗位变更'),
 ('employee-status-change:approve','审批员工任职状态','IDENTITY','审批离职和复职申请'),
 ('employee-role-change:approve','审批员工角色','IDENTITY','审批员工和管理员角色变更');

insert into role_permission(role_id,permission_id,data_scope)
select r.id,p.id,'SELF' from `role` r join permission p on p.code in
 ('member-change:create:self','member-change:query:self','member-change:cancel:self')
where r.code in ('READER','LIBRARIAN','ADMIN');

insert into role_permission(role_id,permission_id,data_scope)
select r.id,p.id,'ALL' from `role` r join permission p on p.code in
 ('member-change:query:reader','reader-qualification:approve','borrow-quota-change:approve')
where r.code in ('LIBRARIAN','ADMIN');

insert into role_permission(role_id,permission_id,data_scope)
select r.id,p.id,'ALL' from `role` r join permission p on p.code in
 ('member-change:query:employee','member-account-change:approve',
  'employee-profile-change:approve','employee-status-change:approve',
  'employee-role-change:approve')
where r.code = 'ADMIN';

insert into permission (code, name, module, description) values
    ('book-category:query', '查询图书分类', 'BOOK', '查询图书分类'),
    ('book-category:create', '新增图书分类', 'BOOK', '新增图书分类'),
    ('book-category:update', '修改图书分类', 'BOOK', '修改图书分类'),
    ('book-category:delete', '删除图书分类', 'BOOK', '删除图书分类'),
    ('book:query', '查询图书', 'BOOK', '查询图书书目'),
    ('book:create', '新增图书', 'BOOK', '新增图书书目'),
    ('book:update', '修改图书', 'BOOK', '修改图书书目'),
    ('book:delete', '删除图书', 'BOOK', '删除图书书目'),
    ('book-copy:query', '查询图书副本', 'BOOK', '查询图书副本'),
    ('book-copy:create', '新增图书副本', 'BOOK', '新增图书副本'),
    ('book-copy:update', '修改图书副本', 'BOOK', '修改图书副本'),
    ('book-copy:delete', '删除图书副本', 'BOOK', '删除图书副本');

insert into role_permission (role_id, permission_id, data_scope)
select r.id, p.id, 'NONE' from `role` r join permission p on p.module = 'BOOK'
where r.code in ('LIBRARIAN', 'ADMIN')
   or (r.code = 'READER' and p.code in ('book-category:query', 'book:query'));

insert into permission(code,name,module,description) values
 ('borrow-application:create','提交借阅申请','BORROW','提交本人借阅申请'),
 ('borrow-application:query','查询借阅申请','BORROW','查询借阅申请'),
 ('borrow-application:cancel','取消借阅申请','BORROW','取消本人待审批借阅申请'),
 ('borrow-application:approve','批准借阅申请','BORROW','批准借阅申请'),
 ('borrow-application:reject','拒绝借阅申请','BORROW','拒绝借阅申请'),
 ('borrow-record:query','查询借阅记录','BORROW','查询借阅记录'),
 ('borrow-record:return','归还图书','BORROW','办理图书归还');

insert into role_permission(role_id,permission_id,data_scope)
select r.id,p.id,case when r.code in ('LIBRARIAN','ADMIN') then 'ALL' else 'SELF' end
from `role` r join permission p on p.module='BORROW'
where r.code in ('READER','LIBRARIAN','ADMIN')
and (r.code!='READER' or p.code in ('borrow-application:create','borrow-application:query','borrow-application:cancel','borrow-record:query'));

insert into permission(code,name,module,description) values
 ('reading-plan:query:self','查询本人阅读计划','READING','查询本人阅读计划及条目'),
 ('reading-plan:create:self','新增本人阅读计划','READING','新增本人阅读计划及条目'),
 ('reading-plan:update:self','修改本人阅读计划','READING','修改本人阅读计划及条目'),
 ('reading-plan:delete:self','删除本人阅读计划','READING','删除本人阅读计划及条目'),
 ('reading-plan:query','查询阅读计划','READING','管理端查询阅读计划及条目'),
 ('reading-plan:create','新增阅读计划','READING','管理端新增阅读计划及条目'),
 ('reading-plan:update','修改阅读计划','READING','管理端修改阅读计划及条目'),
 ('reading-plan:delete','删除阅读计划','READING','管理端删除阅读计划及条目');

insert into role_permission(role_id,permission_id,data_scope)
select r.id,p.id,'SELF' from `role` r join permission p on p.code like 'reading-plan:%:self'
where r.code in ('READER','LIBRARIAN','ADMIN');

insert into role_permission(role_id,permission_id,data_scope)
select r.id,p.id,'ALL' from `role` r join permission p on p.code in
 ('reading-plan:query','reading-plan:create','reading-plan:update','reading-plan:delete')
where r.code in ('LIBRARIAN','ADMIN');

INSERT INTO permission(code,name,module,description) SELECT 'conversation:create:self','conversation:create:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='conversation:create:self');

INSERT INTO permission(code,name,module,description) SELECT 'conversation:query:self','conversation:query:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='conversation:query:self');

INSERT INTO permission(code,name,module,description) SELECT 'conversation:update:self','conversation:update:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='conversation:update:self');

INSERT INTO permission(code,name,module,description) SELECT 'message:send:self','message:send:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='message:send:self');

INSERT INTO permission(code,name,module,description) SELECT 'message:query:self','message:query:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='message:query:self');

INSERT INTO permission(code,name,module,description) SELECT 'message:read:self','message:read:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='message:read:self');

INSERT INTO permission(code,name,module,description) SELECT 'websocket:connect:self','websocket:connect:self','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='websocket:connect:self');

INSERT INTO permission(code,name,module,description) SELECT 'message:send:multiple','message:send:multiple','COMMUNICATION','第一版通信权限' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='message:send:multiple');

INSERT INTO role_permission(role_id,permission_id,data_scope) SELECT r.id,p.id,'SELF' FROM `role` r JOIN permission p ON p.module='COMMUNICATION' WHERE r.code IN ('READER','LIBRARIAN','ADMIN') AND (p.code <> 'message:send:multiple' OR r.code IN ('LIBRARIAN','ADMIN')) AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO permission(code,name,module,description)
SELECT 'contact:query','通信联系人查询','COMMUNICATION','只返回允许联系的成员摘要'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='contact:query');

INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,CASE WHEN r.code='READER' THEN 'NONE' ELSE 'ALL' END
FROM `role` r JOIN permission p ON p.code='contact:query'
WHERE r.code IN ('READER','LIBRARIAN','ADMIN')
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

insert into policy_version(type,title,content,parameters_json,status,policy_version,published_at)
values ('ADMISSION','Admission policy','Verified email and current borrowing rules are required.',
'{"requireVerifiedEmail":true,"requiredFields":["NAME"],"defaultMaxBorrowCount":5}','ACTIVE',1,current_timestamp(3));

insert into policy_version(type,title,content,parameters_json,status,policy_version,published_at)
values ('BORROWING','Borrowing rules','Return on time. Renewal requires approval.',
'{"defaultBorrowDays":30,"maxBorrowDays":60,"maxRenewCount":3,"defaultRenewDays":30,"maxRenewDays":60,"blockBorrowWhenOverdue":true,"applicationValidityDays":7}','ACTIVE',1,current_timestamp(3));

insert into policy_current(type,policy_id,sequence_value) select type,id,policy_version from policy_version;

insert into permission(code,name,module,description) values('policy:query','policy:query','POLICY','Policy management');

insert into permission(code,name,module,description) values('policy:create','policy:create','POLICY','Policy management');

insert into permission(code,name,module,description) values('policy:update','policy:update','POLICY','Policy management');

insert into permission(code,name,module,description) values('policy:publish','policy:publish','POLICY','Policy management');

insert into role_permission(role_id,permission_id,data_scope) select r.id,p.id,'ALL' from role r join permission p on p.module='POLICY' where r.code='ADMIN';

insert into interest_tag (code,name) values
 ('JAVA','Java'),('BACKEND','后端开发'),('DATABASE','数据库'),
 ('LITERATURE','文学'),('HISTORY','历史'),('SCIENCE','科学'),('ARTIFICIAL_INTELLIGENCE','人工智能');

insert into permission (code,name,module,description) values
 ('recommendation:query:self','查询本人推荐','recommendation','仅当前批次'),
 ('recommendation:refresh:self','刷新本人推荐','recommendation','每次最多六本'),
 ('recommendation:feedback:self','提交推荐反馈','recommendation','本人当前推荐条目'),
 ('interests:update:self','修改本人兴趣','recommendation','标签全量替换');

insert into role_permission(role_id,permission_id,data_scope)
 select r.id,p.id,'SELF' from `role` r cross join permission p
 where r.code in ('READER','LIBRARIAN','ADMIN')
 and p.code in ('recommendation:query:self','recommendation:refresh:self','recommendation:feedback:self','interests:update:self');

insert into permission(code,name,module,description)
 values('ai:chat:self','使用本人AI咨询','ai','只读咨询，会话归属于当前成员');

insert into role_permission(role_id,permission_id,data_scope)
 select r.id,p.id,'SELF' from `role` r cross join permission p
 where r.code in ('READER','LIBRARIAN','ADMIN') and p.code='ai:chat:self';

insert into permission(code,name,module,description) values
 ('ai:business:self','使用本人AI业务助手','ai','仅生成和确认本人的操作草稿，具体业务另行鉴权');

insert into role_permission(role_id,permission_id,data_scope)
 select r.id,p.id,'SELF' from `role` r cross join permission p
 where r.code in ('READER','LIBRARIAN','ADMIN') and p.code='ai:business:self';

insert into permission(code,name,module,description) values
 ('operation-log:query','查询操作审计','audit','查询全系统脱敏操作记录，需ALL数据范围');
insert into role_permission(role_id,permission_id,data_scope)
 select r.id,p.id,'ALL' from role r cross join permission p
 where r.code='ADMIN' and p.code='operation-log:query';

-- 知识内容保存在现有Python知识库目录/SQLite，MySQL只增加后台授权。
INSERT INTO permission(code,name,module,description)
SELECT 'knowledge:query','查询知识库','ai','查看知识文档、索引进度和检索测试，要求ALL'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='knowledge:query');
INSERT INTO permission(code,name,module,description)
SELECT 'knowledge:manage','维护知识文档','ai','维护受控知识正文与状态，要求ALL'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='knowledge:manage');
INSERT INTO permission(code,name,module,description)
SELECT 'knowledge:rebuild','重建知识索引','ai','创建异步索引任务，要求ALL'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='knowledge:rebuild');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'ALL' FROM role r CROSS JOIN permission p
WHERE r.code='ADMIN' AND p.code IN('knowledge:query','knowledge:manage','knowledge:rebuild')
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO ai_runtime_control(code) SELECT 'GLOBAL' WHERE NOT EXISTS(SELECT 1 FROM ai_runtime_control WHERE code='GLOBAL');
INSERT INTO ai_runtime_control(code) SELECT 'CONSULTATION' WHERE NOT EXISTS(SELECT 1 FROM ai_runtime_control WHERE code='CONSULTATION');
INSERT INTO ai_runtime_control(code) SELECT 'BUSINESS' WHERE NOT EXISTS(SELECT 1 FROM ai_runtime_control WHERE code='BUSINESS');
INSERT INTO ai_runtime_control(code) SELECT 'RECOMMENDATION' WHERE NOT EXISTS(SELECT 1 FROM ai_runtime_control WHERE code='RECOMMENDATION');
INSERT INTO permission(code,name,module,description)
SELECT 'ai-task:query','查询AI任务','ai','SELF查看本人任务，ALL查看全局任务' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-task:query');
INSERT INTO permission(code,name,module,description)
SELECT 'ai-task:manage','管理AI任务','ai','SELF重试或取消本人任务，ALL可取消其他任务' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-task:manage');
INSERT INTO permission(code,name,module,description)
SELECT 'ai-control:manage','管理AI能力','ai','全局暂停和独立能力控制，要求ALL' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-control:manage');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,CASE WHEN r.code='ADMIN' THEN 'ALL' ELSE 'SELF' END
FROM role r CROSS JOIN permission p
WHERE ((r.code IN ('READER','LIBRARIAN','ADMIN') AND p.code IN('ai-task:query','ai-task:manage')) OR (r.code='ADMIN' AND p.code='ai-control:manage'))
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO permission(code,name,module,description) SELECT 'ai-handoff:create','请求AI转人工','ai','SELF本人申请，摘要由本人确认' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-handoff:create');
INSERT INTO permission(code,name,module,description) SELECT 'ai-handoff:query','查询转人工','ai','SELF本人或接收人，ALL全局' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-handoff:query');
INSERT INTO permission(code,name,module,description) SELECT 'ai-handoff:accept','接管AI咨询','ai','仅指定的在职馆员可接管' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='ai-handoff:accept');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,CASE WHEN r.code='ADMIN' THEN 'ALL' ELSE 'SELF' END FROM role r CROSS JOIN permission p
WHERE ((r.code IN ('READER','LIBRARIAN','ADMIN') AND p.code IN('ai-handoff:create','ai-handoff:query')) OR (r.code IN ('LIBRARIAN','ADMIN') AND p.code='ai-handoff:accept'))
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO permission(code,name,module,description) SELECT 'operations:query','查询运营统计','operations','ALL查看统计和本人/授权作业' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='operations:query');
INSERT INTO permission(code,name,module,description) SELECT 'operations:import','批量导入馆藏','operations','导入仍逐行调用原书目副本服务鉴权' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='operations:import');
INSERT INTO permission(code,name,module,description) SELECT 'operations:export','导出馆藏','operations','导出已授权的书目字段，不含人员敏感资料' WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='operations:export');
INSERT INTO role_permission(role_id,permission_id,data_scope)
SELECT r.id,p.id,'ALL' FROM role r CROSS JOIN permission p
WHERE r.code IN('LIBRARIAN','ADMIN') AND p.code IN('operations:query','operations:import','operations:export')
AND NOT EXISTS(SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);


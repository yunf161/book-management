# Book Management 项目规划（整体 + 阶段 A）

## 1. 项目整体阶段规划

### 阶段 A：领域模型与核心业务（当前阶段）
- 目标：仅用 Java 完成人员/书籍/借还流程核心逻辑，不接数据库和前端。
- 产出：可运行的类模型、服务层、内存仓储、单元测试、控制台演示流程。

### 阶段 B：分层稳定与接口化
- 目标：固化 `domain/service/repository/app` 分层，补齐查询与异常处理规范。
- 产出：稳定 API 风格（先控制台或简单 REST）、更完整测试。

### 阶段 C：持久化与权限
- 目标：接入 Spring Boot + MySQL（或 PostgreSQL），替换内存仓储。
- 产出：Repository 数据库实现、迁移脚本、登录鉴权、角色权限。

### 阶段 D：前端管理端
- 目标：实现管理员/馆员/读者界面与主要业务流程。
- 产出：图书管理、人员管理、借还书、历史记录等页面。

### 阶段 E：企业级增强与 AI
- 目标：提高可用性、可观测性与智能能力。
- 产出：缓存、审计日志、监控告警、推荐与问答等 AI 功能。

---

## 2. 阶段 A 详细开发计划

## 2.1 范围与边界
- 只做后端 Java 代码（无数据库、无前端、无网络接口强依赖）。
- 仓储先用内存结构（`Map/List`）。
- 核心覆盖：人员管理、图书副本管理、借书、还书、逾期判断。

## 2.2 包结构（已初始化）
- `src/main/java/com/yunf161/bookmgmt/domain`
- `src/main/java/com/yunf161/bookmgmt/service`
- `src/main/java/com/yunf161/bookmgmt/repository`
- `src/main/java/com/yunf161/bookmgmt/exception`
- `src/main/java/com/yunf161/bookmgmt/app`
- `src/test/java/com/yunf161/bookmgmt`

## 2.3 领域对象设计（先写）

### 人员
1. `Person`（抽象）
   - 字段：`id, name, phone, status`
2. `Reader extends Person`
   - 字段：`readerCode, maxBorrowLimit, currentBorrowCount`
3. `Librarian extends Person`
   - 字段：`employeeCode`

### 图书
1. `Book`
   - 字段：`isbn, title, author, category`
2. `BookCopy`
   - 字段：`copyId, isbn, location, status`
   - 状态建议：`AVAILABLE, BORROWED, LOST, OFF_SHELF`

### 借阅
1. `LoanRecord`
   - 字段：`recordId, readerId, copyId, borrowTime, dueTime, returnTime, status`
   - 状态建议：`BORROWING, RETURNED, OVERDUE`

## 2.4 Repository（内存实现）
1. 定义接口（可选按实体拆分）
   - `ReaderRepository`
   - `BookRepository`
   - `BookCopyRepository`
   - `LoanRecordRepository`
2. 提供 InMemory 实现
   - 用 `ConcurrentHashMap`/`HashMap` + 必要索引
   - 负责增删改查，不放业务规则

## 2.5 Service（业务规则实现）
1. `ReaderService`
   - 注册读者、冻结/解冻、查询读者
2. `BookService`
   - 新增图书、增加副本、下架副本、查询可借副本
3. `LoanService`
   - 借书：校验读者状态、借阅上限、副本可借状态
   - 还书：更新借阅记录与副本状态
   - 查询当前借阅、查询逾期记录

## 2.6 业务规则清单（必须落地）
1. 读者被冻结不可借书。
2. 借阅数量达到上限不可继续借书。
3. `BookCopy` 非 `AVAILABLE` 不可借。
4. 还书后必须同步：
   - 借阅记录 `status -> RETURNED`，写入 `returnTime`
   - 副本状态 `-> AVAILABLE`
5. `now > dueTime` 判定逾期。
6. 违反规则必须抛业务异常，不能静默失败。

## 2.7 异常设计
- `NotFoundException`
- `BusinessRuleException`
- 异常信息要明确包含关键 ID（readerId/copyId/recordId）

## 2.8 测试计划（阶段 A 核心）
至少实现以下测试：
1. 借书成功
2. 超上限借书失败
3. 已借出副本重复借书失败
4. 还书成功并恢复副本状态
5. 逾期判断正确
6. 冻结读者借书失败

## 2.9 演示入口（Main）
在 `app/Main.java` 写最小演示流程：
1. 创建读者和馆员
2. 录入图书与副本
3. 执行借书
4. 执行还书
5. 输出借阅记录与副本状态

## 2.10 执行顺序（建议 7 天）
1. Day 1：实体类 + 枚举（状态类）  
2. Day 2：Repository 接口 + InMemory 实现  
3. Day 3：ReaderService/BookService  
4. Day 4：LoanService（借书/还书主流程）  
5. Day 5：异常处理与规则收口  
6. Day 6：JUnit 测试补齐  
7. Day 7：Main 演示 + 代码整理

## 2.11 阶段 A 完成标准（验收）
- 代码可编译、测试通过。
- 借还主流程可运行且规则完整。
- `service` 与 `repository` 解耦，后续可无痛替换数据库实现。
- 目录结构清晰，命名一致。

---

## 3. 开发命令（项目根目录）
```powershell
cd D:\develop\java\book-management
mvn test
```

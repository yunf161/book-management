# API约定与关键业务契约

## 地址与返回值

Java默认地址 `http://localhost:8080`；前端开发代理前缀 `/api`。参考文档中的路径不包含这个代理前缀。当前接口保持聚合，不另造按ID/名称的平行查询接口。

普通成功响应：

```json
{"code":200,"msg":"success","data":{"total":1,"rows":[]}}
```

这里是结构示意，真实 `total` 与 `rows` 来自查询，不能复制示例当测试断言。失败响应保留同一包装，`data` 通常为null。HTTP与业务码通常一致：400字段/格式、401未登录或凭据失效、403权限/规则限制、404不存在、409状态或版本冲突、422业务条件不满足、429频率限制、503依赖不可用、500未预期故障。具体场景以异常处理器与Service为准。

例外：AI流式回答是SSE；运营下载是Excel二进制；封面content接口返回302跳转。不得统一按JSON解析。

## 登录、授权与须知

- 请求携带 `Authorization: Bearer <JWT>`。成功解析JWT后仍需存在有效Redis会话。
- 刷新返回头 `X-Refreshed-Token`，前端及时替换本地token；旧JTI只在配置的宽限窗口有效。
- 服务端使用可信LoginUserContext中的roleId查询permission与DataScope，不使用前端提交的身份授权。
- 未接受最新有效借阅须知的已登录会话仍受限制。先查询 `/me/borrowing-rules`，再由用户显式提交decision，不能由客户端伪造acceptedRuleVersion。
- 读者注册的memberType为READER；员工为EMPLOYEE并需有效departmentId、positionName。没有开放管理员/AI成员注册。
- 验证码通过同一个 `/login/code` 入口发送；注册和重置的email/password/code来自相应DTO，不能跳过验证码服务端校验。

## 查询与版本

- 多数接口使用 `page/pageSize`；阅读计划使用 `pageNum/pageSize`，以对应DTO为准，不能混发两套分页名。
- 空查询条件不应被前端自动转为有意义的0或false；这两种值本身可能是合法条件。
- 人员查询要求memberType，读者与员工扩展表由后端分支选择；`scene=CONTACT`返回通信联系人摘要，使用独立联系人范围规则。
- `id`与其他条件同时出现时不能直接返回未过滤的缓存；聚合查询的筛选语义以Mapper条件为准。
- `expectedVersion`、条目version、expectedPlanVersion等来自最近查询结果，由系统自动携带。记录创建的初始版本由后端设置；不是所有接口都统一接受相同版本字段。
- 新资料申请的成员/资料快照版本由服务端在锁内获取，不要求申请人输入内部版本。DTO为兼容可能仍有相关属性，不表示客户端应手填它们。
- 版本冲突时应重新查询并让用户核对，不无限重试覆盖别人的修改。

自动字段索引只证明DTO声明了该属性，不证明任意属性都可被任意角色更新，或任意DTO属性都对应实际查询条件。实际白名单、SQL条件与跨字段校验见Service/Mapper。

## 主要写入场景

| 场景 | 关键请求与限制 |
| --- | --- |
| 分类创建/更新 | 创建code/name；更新name/parentId/description/status/expectedVersion；code创建后不改，父分类需启用且不得循环 |
| 书目维护 | categoryId/title/author等出版信息；ISBN空白转null，非空唯一；更新带expectedVersion；新分类须启用 |
| 副本维护 | 所属bookId只从新增路径取；barcode/location等；普通状态维护不能直接借出或归还 |
| 借阅 | POST `/borrow-applications`：applicationType=BORROW、bookId、requestedDays、reason |
| 续借 | 同一入口：applicationType=RENEW、borrowRecordId、requestedDays、reason；原记录/次数/政策二次检查 |
| 审批 | 申请ID从路径；批准天数、说明和查询得到的expectedVersion；BORROW可选copyId，RENEW不能另指定副本 |
| 归还 | 记录或条码入口；returnOutcome=NORMAL/DAMAGED/LOST、returnConditionNote、可信查询版本 |
| 资料申请 | applicationType与相应申请字段；长期/临时额度、调岗、角色变更等不能混用无关字段 |
| 阅读条目 | 图书、顺序、状态、进度等；修改/删除同时维护条目和所属计划版本 |
| 业务AI确认 | 先preview返回具体提案，再提交该提案的requestHash；确认时仍执行原业务权限与状态检查 |

借阅提交时的预检不是审批保证，审批时必须重新校验额度和库存。续借不会重复占用额度。归还即使当前额度降低也需合法完成，不能因额度问题阻止结案。

## 消息与实时通道

POST `/messages` 使用 `conversationId`或适用的recipientIds，以及subject/body/clientMessageId。`clientMessageId`由客户端为**一次发送操作**生成唯一ID（如UUID）；网络重试复用该ID，下一条消息使用新ID。不同内容复用同一ID应拒绝，不是一个连接共用一个消息ID。

WebSocket连接流程：登录 → POST `/ws/tickets`取得短期一次性票据 → 连接返回的 `/ws/events?ticket=...`。不在URL中携带长期JWT或客户端指定的成员身份。上线后仍通过HTTP补查未读消息，收到实时提醒不代表数据已经在客户端持久化。

## AI与开放Map请求

`POST /ai/chat`：question、可选conversationId、task（咨询/业务）。SSE事件包括meta/delta/sources/proposal/usage/done/error。以done结束才将本轮视为完成；断流时不把半轮文字作为已完成业务凭据。

Java知识管理接口使用开放Map，不代表任意字段都被接受：

| 入口 | 允许字段/行为 |
| --- | --- |
| GET `/admin/knowledge/documents` | page、pageSize、id、title、kind、status |
| POST/PUT `/admin/knowledge/documents[/{id}]` | title、source、kind、version、status、content、expectedVersion、importMetadata；具体必填与长度由知识管理服务校验 |
| POST `/admin/knowledge/rebuild` | expectedRevision、expectedGeneration，来自当前目录状态 |
| POST `/admin/knowledge/search` | question、kind、topK；topK为1～20 |
| POST `/admin/knowledge/import-preview` | multipart字段file；支持MD/TXT/CSV/JSON/文本PDF |

Java对外接口已经鉴权，内部Python管理协议另需服务间凭据。内部服务不作为公开API网关；开发时的内部健康和工具协议不纳入对外127项路由清单。

## 封面、下载与安全

封面上传/更换使用multipart的file及查询得到的expectedVersion。数据库保存对象key，图书查询返回封面入口；`GET /books/{bookId}/cover/content`生成短期签名并302跳转，不能把该签名当永久地址。

运营导入按模板处理，下载接口需要作业访问权限。前端隐藏密码摘要、JWT等敏感字段；API文档不附带真实账号、token、数据库连接密码或模型密钥。

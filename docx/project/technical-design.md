# 技术设计清单

| 主题 | 实际设计与目的 | 源码入口 | 关键边界 |
| --- | --- | --- | --- |
| 模块化单体 | Java按identity/book/communication/policy/recommendation/ai等业务分包 | `src/main/java/com/yunfeil161/ailibrary/` | 非微服务；Python是独立AI执行服务 |
| SQL与分页 | MyBatis XML、驼峰映射，Service启动PageHelper；保留聚合查询 | `src/main/resources/mapper/`、各Service | 模糊条件、ID、状态组合要真实进入SQL，不被ID缓存短路 |
| 领域建模 | Reader聚合三张表，Employee聚合成员和员工资料 | `identity/entity/`、`identity/mapper/` | 领域对象不要求与单表一一对应 |
| 角色授权 | roleId＋permission.code联查授权与DataScope | `config/security/permission/`、`config/security/role/` | memberType只区分资料，不比较类型大小继承权限 |
| 登录会话 | JWT签名校验＋Redis会话；临近到期轮换JTI，短期兼容旧令牌 | `auth/`、`config/security/jwt/` | JWT仍有效不代表会话未撤销；ThreadLocal请求结束清理 |
| 最新规则 | policy_current指向有效版本；登录/资格/借阅核对权威版本 | `policy/` | 资料接口不能伪造acceptedRuleVersion替代显式接受 |
| 乐观锁 | WHERE id/version，更新成功version＋1 | 各Mapper XML、`modules/recordVersions.js` | 前端自动携带，不让读者手填；影响0行与多行分别处理 |
| 资源锁 | Redisson可重入读写锁；固定资源获取顺序与反向释放 | `common/redis/lock/` | 只覆盖明确接入场景，不宣称全部列表查询都加锁 |
| 数据库锁 | 业务事务与必要FOR UPDATE协调多行变更 | 借阅、阅读计划与审批Mapper | Redis锁不替代数据库约束、事务或乐观锁 |
| 缓存 | 按ID缓存、空值防穿透、随机TTL；写入时删除并重试 | `common/redis/method/CacheClient.java`、各缓存键类 | MySQL和Redis不是跨库原子事务；失败路径需监测，禁止描述为强一致平台 |
| 借还并发 | 在资源锁和事务内重新核对资格、规则、额度、库存 | `book/borrow/` | 提交预检不代替审批时二次检查；归还不能重复扣减 |
| 去重/幂等 | 请求ID与requestHash绑定不可变请求；待办重复判定结合资源锁/Redis脚本 | `communication/`、`ai/business/`、借阅服务 | 同ID不同内容必须拒绝；不能仅依靠“先查再插” |
| Outbox | 消息与事件先落库，发布确认后标记 | `communication/event/`及相关消息投递代码 | 数据库提交与Broker发布不天然原子，允许重试投递 |
| RabbitMQ | 同一事件进入索引、提醒两条职责队列，分别消费和ACK | `communication/` | 按至少一次投递设计，消费者需幂等；有界重试/死信不等于自动完成业务补偿 |
| 实时通信 | 一次性短期票据握手，WebSocket推送提醒，HTTP查询与已读 | `communication/websocket/` | 消息数据库是事实来源；离线不依赖WebSocket持久化 |
| 读写竞态 | 已读以数据库收件关系为准，避免积压索引任务重新写入已读消息 | 通信索引消费与已读服务 | 已读标记丢失仍需数据库兜底；不能仅靠缓存存在性 |
| 定时任务 | 逾期、申请过期、临时额度清理、任务核对等 | `*Scheduler.java`、AI核对服务 | 集群场景还需核对每项任务的协调方式，不能假设单实例调度即分布式调度 |
| OSS | 后端核验封面、保存对象key、查询时生成访问入口/签名 | `book/cover/` | 不保存二进制到数据库，不缓存长期有效私有签名 |
| Excel | Apache POI模板、分行校验、异步作业、部分成功报告 | `operations/` | 授权下载、大小/行数限制、重复导入保护 |
| 推荐 | 候选由规则召回，兴趣/反馈排序，AI仅候选内重排 | `recommendation/`、Python重排代码 | 没有训练推荐模型，不将规则排序说成协同过滤或深度推荐 |
| RAG | 受控知识切块、嵌入式Chroma检索、来源返回、MD5去重 | `ai-service/rag/` | 文档型知识与实时关系数据分开；数据库信息通过工具查询 |
| 模型降级 | 百炼兼容接口优先，失败回退Ollama | `ai-service/clients/`、`config/` | 不能保证任意故障下仍成功；本地模型/硬件需自行配置 |
| 业务智能体 | 提示词＋Skill/工具白名单＋提案确认＋Java鉴权 | `ai/business/`、`ai-service/agents/business.py` | 模型输出不作为授权依据；确认前不执行写操作 |
| SSE | 转发受限事件并处理done/error/断流 | `ai/controller/AiChatController.java` | 收到部分token不等于业务成功；确认操作仍走HTTP |
| 审计 | AOP采集业务前后快照，白名单脱敏后持久化查询 | `audit/` | 审计失败记录与告警需运维跟踪；不称作合规认证 |
| 测试 | H2/MockMvc、HTTP、Redis/MQ专项、前端组件与Python测试 | `src/test/`、`frontend/tests/`、`ai-service/tests/` | 本机测试不等于生产压测或安全审计 |

实际依赖版本以 `pom.xml`、`frontend/package-lock.json`、`ai-service/requirements.txt` 为准，避免文档固定版本与锁文件漂移。当前pom设置Java17，已有本机验收使用JDK21；应在目标JDK上重复构建确认。

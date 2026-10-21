# 运行、初始化与验证

## 环境

| 依赖 | 说明 |
| --- | --- |
| Java | pom目标Java17；本机已使用JDK21验收，建议新环境固定同一JDK版本验证 |
| Maven | 根目录Wrapper指定版本，首次需下载；也可使用兼容Maven |
| Node.js | 测试脚本使用Node内置test及VM模块，建议匹配已验收的Node24或更新兼容版本；先确认命令支持所用参数 |
| Python | 当前AI依赖基于Python3.12环境；安装ai-service/requirements.txt |
| MySQL | MySQL8，空库结构使用utf8mb4和毫秒精度；不建外键 |
| Redis | 会话、缓存、读写锁与索引；不要把测试库用于生产 |
| RabbitMQ | 应用vhost与账号需预先创建授权；AMQP端口与管理控制台端口不是一回事 |
| 模型/嵌入 | 百炼API或Ollama聊天；RAG重建仍需要可用的嵌入模型服务 |
| 邮件/OSS | 注册验证码、密码重置和封面上传需要独立SMTP/OSS配置 |

这些是当前实现对应的环境要求，不是安装脚本。源码包不包含依赖安装包、模型文件或云端账号。

## 配置与初始化

1. 复制 `deploy/environment.example` 中需要的键到运行环境或被忽略的 `local-secrets.properties`，填入自己的值。Python不会自动读取Java properties；统一启动脚本会向子进程传递必要配置，独立启动时须显式设置环境变量。
2. 用低权限应用账号连接MySQL。空数据库先执行 `database/00-release/000-schema.sql`，再执行 `001-seed.sql`。先检查文件内容和数据库名，不能对已有库直接执行空库脚本。
3. 新装种子只初始化公共角色、权限、政策和字典，**不附带可登录管理员密码**。首次管理员应由部署人员在受控初始化过程中建立：有效ADMIN角色、EMPLOYEE类型member、匹配的employee_profile、有效部门；密码使用Spring BCryptPasswordEncoder生成摘要，不写明文，不通过公开注册接口自行成为管理员。
4. 管理员初始化是部署前置事项，不是本项目已提供的免配置按钮。初始化后需自行核对角色权限、当前政策和邮箱流程，不能导入本机验收账号数据库。
5. 已有库先备份，按已应用版本选择升级目录。目录编号不等于可以对任意库盲目全量重跑；部分历史脚本只适用于对应旧结构。
6. RAG使用内置说明资料 `ai-service/knowledge/library-guide`；首次重建后索引写到被忽略的data目录。不复制开发者本机私有知识或索引。

## 启动与停止

Windows项目根目录：

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
cd frontend
npm ci
npm test
npm run build
cd ..
# AI_PYTHON指向自己的Python解释器，不要求使用开发者原有Conda目录。
$env:AI_PYTHON = 'C:\path\to\python.exe'
& $env:AI_PYTHON -m pip install -r ai-service/requirements.txt
.\start.ps1 -Action Check
.\start.ps1
.\start.ps1 -Action Status
```

统一启动脚本会复用已核验的服务，不新建MySQL/Redis/MQ容器。它默认支持本地Ollama核显配置，但硬件与模型需自行准备。只用云端时可用 `-SkipOllama`；这不意味着RAG嵌入模型不再需要服务。

停止本脚本管理的进程：`.\start.ps1 -Action Stop`。它不会主动停止外部IDEA进程和复用的依赖。Java默认8080、前端5173、内部AI8001；端口改动需同步Vite代理、Java内部回调和反向代理配置。

Linux等环境可独立启动各服务：构建Java后运行JAR；在frontend执行Vite或部署dist；在ai-service环境运行 `python ai_server.py --help` 查看启动参数。Windows统一脚本不是Linux部署编排文件。

## 测试命令

```powershell
.\mvnw.cmd test
npm --prefix frontend test
cd ai-service
python -m unittest discover -s tests -p 'test_*.py'
cd ..
# 仅导出接口结构，不请求生产业务或模型。
.\mvnw.cmd '-Dtest=ApiInventoryTests' '-Ddocs.export=true' test
python scripts/generate-api-docs.py
```

Java测试使用H2、关闭正式JWT拦截或自行构造可信测试上下文，并单独验证真实权限逻辑；某些Redis相关测试会访问隔离库15，应使用独立测试实例或正确配置认证。测试通过不意味着任意依赖故障都无影响。

Python测试主要模拟模型和内部协议；真实模型推理、云端计费、SMTP送达、OSS读写与WebSocket/MQ跨服务联调需要专门环境，不包含在每次单元测试中。

## 目前证据与限制

2026-10-21干净源码快照已验证364项Java测试（含接口结构校验）、190项Python测试、75项前端测试及构建通过。上轮33项退役专项HTTP检查是独立历史证据；重新运行后的结果应以自己的日志为准，不直接沿用这些数字。

源码导出会生成文件清单与SHA-256，并检查高置信密钥模式、私有文件名与文档链接。它不能替代人工代码评审、依赖审计、渗透测试或生产性能测试。

正式上线还需独立域名/HTTPS、备份和恢复演练、日志与死信告警、生产管理员初始化、凭据轮换、模型外发策略、资源限制和目标环境验收。当前不提供“一键公网生产上线”承诺。

## 搬迁后复验

2026-10-21在交付目录重新构建并运行：Java 364项、Python 190项、前端75项测试通过；33项真实HTTP冒烟通过。服务进程确认来自新目录。范围与限制见[本地运行验收](local-run-20261021.md)。

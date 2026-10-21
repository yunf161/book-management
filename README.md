# 阅知 · AI 图书馆

面向读者、馆员和管理员的图书馆业务系统。以 Java 模块化单体处理事务与权限，以 Vue 提供操作界面，以独立 Python 服务提供咨询、RAG 和需要用户确认的业务智能体。

本项目为个人开发与本机联调项目，包含可运行源码和测试；不宣称已有真实机构上线、生产用户量或未经测量的并发性能。

## 核心能力

- **图书业务闭环**：分类、书目、副本、封面，借阅申请、审批、续借、正常/损坏/遗失归还、逾期和额度控制。
- **身份与治理**：JWT＋Redis 会话、角色权限及数据范围、个人资料和敏感变更审批、政策版本与须知确认、脱敏审计。
- **服务与运营**：阅读计划、当前推荐与反馈、私聊/多收件人消息、离线接收、Excel 导入导出及作业报告。
- **AI 辅助**：咨询与业务两个智能体；知识导入、检索和重建；工具调用、业务提案确认、任务管理、能力开关和人工交接。阿里云模型优先，失败降级本地 Ollama。

AI 是服务，不是成员账号；业务操作继续使用当前人类用户的 Java 权限与事务校验。

## 技术栈与结构

| 层次 | 当前实现 |
| --- | --- |
| 后端 | Java、Spring Boot 4、MyBatis、PageHelper、MySQL |
| 并发与缓存 | Redis、Redisson 资源读写锁、乐观锁、数据库事务/行锁 |
| 通信 | RabbitMQ、事务 Outbox、WebSocket、SSE |
| AI | Python、阿里云兼容接口、Ollama、嵌入式 Chroma、RAG、Skill 工具边界 |
| 前端与文件 | Vue 3、Vite、Element Plus、阿里云 OSS、Apache POI |
| 验证 | JUnit/MockMvc/H2、Node 测试、Python unittest、专项真实 HTTP 检查 |

```text
src/main/           Java业务、MyBatis XML与配置
src/test/           Java集成/单元测试、H2结构
frontend/           Vue界面及前端测试
ai-service/         Python智能体、提示词、知识检索与测试
database/           新装结构、种子与分阶段升级脚本
scripts/            启动、文档生成、发布源码包工具
deploy/             环境变量样例与反向代理配置
docx/project/       当前项目说明、API、设计和简历材料
```

## 阅读入口

| 文档 | 内容 |
| --- | --- |
| [文档导航](docx/project/README.md) | 推荐阅读顺序与交付边界 |
| [功能清单](docx/project/features.md) | 模块、角色、完成范围及不包含内容 |
| [技术设计清单](docx/project/technical-design.md) | 技术选择、实现位置、验证点与限制 |
| [系统设计](docx/project/system-design.md) | 架构、数据关系、关键流程及一致性边界 |
| [数据表索引](docx/project/database-reference.md) | 从当前新装DDL提取的表和字段 |
| [接口约定](docx/project/api-conventions.md) | 鉴权、分页、版本、SSE/WebSocket和业务限制 |
| [接口参考](docx/project/api-reference.md) / [OpenAPI](docx/project/openapi.json) | 实际路由与请求字段，可导入接口工具 |
| [运行与验证](docx/project/runbook.md) | 新环境配置、初始化、启动和测试 |
| [简历与面试材料](docx/project/resume.md) | 简历描述、技术亮点和事实边界 |
| [发布说明](docx/project/release.md) | 源码包范围、敏感配置、Git推送准备 |

## 快速开始

1. 准备 Java、Maven Wrapper、Node.js、Python，以及 MySQL、Redis、RabbitMQ。
2. 配置独立的环境变量；参考 [deploy/environment.example](deploy/environment.example)。不要提交真实密钥。
3. **空库**按 `database/00-release` 初始化；**已有库**先备份，再按已应用的迁移版本升级，不能重放空库结构。
4. 后端执行 `mvnw.cmd test`、`mvnw.cmd -DskipTests package`；前端执行 `npm ci`、`npm test`、`npm run dev`；Python执行依赖安装与预检。完整命令见运行文档。
5. Windows 可使用根目录 `start.ps1` 统一启动。脚本不自动安装依赖、不创建数据库，也不附带管理员密码。

默认开发入口：前端 `http://localhost:5173`，Java `http://localhost:8080`，内部 AI 服务 `http://localhost:8001`。Python AI 和 Redis/MQ 管理端不应直接暴露公网。

## 测试与交付状态

2026-10-21 的干净源码验证：364 项 Java 测试、190 项 Python 测试、75 项前端测试通过，前端离线安装和生产构建成功。搬迁后的目录再次完成同样全量测试，并通过33项真实HTTP冒烟检查；详见 [本地运行验收](docx/project/local-run-20261021.md)及[源码验证记录](docx/project/verification.md)。

公开源码包不包含业务库、测试账号、模型权重、向量索引、本机凭据、日志或旧 Git 历史。源代码和文档足以复现结构与测试；完整运行还需自行配置外部依赖、初始化账号和模型服务。

当前未指定开源许可证；公开仓库不等于授予任意复制、分发或商用授权，发布前由项目所有者选择许可证。

# 架构决策记录

这份文档记录项目中几个核心技术决策。它的作用是帮助自己和面试官快速理解：为什么这样设计，而不是只看到“用了哪些技术”。

## ADR-001：使用 Spring Boot + FastAPI 双后端

决策：

```text
Spring Boot 作为业务后端，FastAPI 作为 AI 服务。
```

原因：

```text
Spring Boot 更适合承载企业业务系统能力：Controller / Service / Repository 分层、MySQL 事务、权限校验、统一响应、异常处理和静态页面入口。

FastAPI 更适合承载 AI 能力：PDF 解析、文本切分、Embedding、Qdrant 检索、Rerank、Prompt 编排、模型 JSON 解析和评测脚本。
```

收益：

```text
业务边界和 AI 边界清晰。
前端只调用 Spring Boot，不直接暴露 FastAPI 和模型服务。
后续扩展其他 AI 场景时，可以复用同一套 Spring Boot + FastAPI 边界。
```

代价：

```text
多了一个服务间 HTTP 调用。
本地启动和排查需要同时关注 Java、Python、MySQL、Qdrant 和 DashScope。
```

## ADR-002：MySQL 保存业务数据，Qdrant 保存向量数据

决策：

```text
MySQL 保存知识库、文档状态、聊天会话、聊天消息、求职分析、生成历史、简历版本和岗位收藏。
Qdrant 保存文本 Chunk 向量和检索 payload。
```

原因：

```text
MySQL 适合保存关系型业务数据和权限归属。
Qdrant 适合做高维向量相似度检索。
业务权限、会话关系和任务历史不适合塞进向量库；高维向量检索也不适合直接放到普通 MySQL 表里做。
```

收益：

```text
权限校验、历史查询、删除和更新都由 Spring Boot + MySQL 管理。
RAG 检索由 FastAPI + Qdrant 管理。
业务数据和向量数据职责清晰，便于解释和排查。
```

代价：

```text
需要维护 MySQL 文档记录和 Qdrant document_id / payload 之间的一致性。
删除文档时要同时处理业务记录和向量数据。
```

## ADR-003：正式问答入口放在 Spring Boot 会话接口

决策：

```text
正式问答使用 /api/chat/sessions/{sessionId}/ask。
旧版 /api/rag/ask 作为不安全入口停用。
```

原因：

```text
RAG 问答必须先校验用户是否能访问当前会话、知识库和文档。
如果前端直接调用简单 RAG 接口并传 documentId，容易绕过业务权限。
```

收益：

```text
权限判断集中在 Spring Boot。
聊天消息可以统一落 MySQL。
documentId 是否属于当前知识库可以在调用 FastAPI 前完成校验。
```

代价：

```text
接口链路更长：前端 -> Spring Boot -> FastAPI -> Qdrant / DashScope。
调试时需要同时查看 Spring Boot 和 FastAPI 日志。
```

## ADR-004：使用 Rerank 和阈值拒答降低幻觉

决策：

```text
保留无 Rerank 基线，同时提供 Rerank RAG 链路。
向量检索扩大候选集，Rerank 后按 rerank_min_score 判断是否拒答。
```

原因：

```text
向量检索只表示语义相似，不一定表示片段能直接支持答案。
企业知识库更怕模型编造答案，因此相关性不足时应该拒答。
```

收益：

```text
Rerank 让更能支持答案的片段进入 Prompt。
拒答阈值让系统在资料不足时明确说明不能回答。
vector_score 和 rerank_score 分开保存，方便定位召回问题还是重排问题。
```

代价：

```text
Rerank 会增加一次模型调用成本和延迟。
阈值需要通过评测集调参，否则可能误拒或误答。
```

## ADR-005：求职辅助 Agent 复用同一套架构

决策：

```text
求职 Agent 继续使用 Spring Boot + FastAPI 边界。
FastAPI 负责 Prompt、模型调用和 JSON 解析；Spring Boot 负责业务接口、userId、MySQL 历史和前端展示。
```

原因：

```text
求职 Agent 本质也是 AI 应用工作流，和 RAG 共用模型调用、Prompt 编排和结构化解析能力。
但它的历史记录、收藏岗位、简历版本和结果对比属于业务数据，应该放在 Spring Boot + MySQL。
```

收益：

```text
证明架构不是只能做知识库问答，也能扩展到新 AI 场景。
生成历史、简历版本、收藏岗位和分析对比都可以持久化和复用。
```

代价：

```text
FastAPI 和 Spring Boot 都需要新增 DTO / schema。
模型输出 JSON 需要更严格的解析和测试覆盖。
```

## ADR-006：提交前检查和 GitHub Actions 使用同一套脚本

决策：

```text
本地运行 scripts/pre_submit_check.sh。
GitHub Actions CI 也运行同一个脚本。
```

原因：

```text
如果本地和 CI 使用两套命令，容易出现本地通过但 CI 失败，或者 CI 通过但本地漏检。
```

收益：

```text
质量入口统一。
每次提交前检查和 GitHub Actions 结果更容易对应。
Markdown 链接、密钥扫描、Docker Compose 配置、FastAPI 测试和 Spring Boot 测试都在同一个入口里。
```

代价：

```text
脚本需要兼容本地 macOS 和 GitHub Actions Ubuntu 环境。
例如 Python 命令要兼容 .venv/bin/python、python3 和 python。
```

## ADR-007：保留 debug.html

决策：

```text
保留原始 debug.html，同时提供 Vue3 企业工作台 index.html。
```

原因：

```text
Vue3 工作台适合演示完整产品体验。
debug.html 适合排查接口请求和响应，尤其在联调、权限错误、字段不匹配时更直接。
```

收益：

```text
演示和排查分开。
面试时可以展示工作台；开发时可以快速定位接口问题。
```

代价：

```text
需要确保静态页面入口都能被测试覆盖，不要出现某个入口长期失效。
```

## ADR-008：使用 Flyway 管理业务库表结构

决策：

```text
Spring Boot 业务库表结构由 Flyway SQL 迁移文件管理。
Hibernate ddl-auto 从自动建表/更新改为 validate，只负责启动时校验 Entity 和数据库结构是否一致。
```

原因：

```text
JPA 自动建表适合学习早期快速迭代，但真实项目需要知道每次表结构变更是什么、何时发生、如何在新环境复现。
Flyway 把数据库结构变成可审查、可提交、可回放的版本化代码。
```

收益：

```text
新环境启动时可以自动创建业务表。
测试环境和本地 MySQL 使用同一套迁移来源。
如果 Entity 和 SQL 不一致，Spring Boot 启动阶段会直接失败，避免静默产生错误表结构。
```

代价：

```text
以后新增字段或表时，不能只改 Entity，还要新增 V2、V3 这类迁移 SQL。
已有本地旧库首次接入 Flyway 时需要建立 baseline，避免重复执行初始化建表脚本。
```

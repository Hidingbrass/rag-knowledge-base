# 完整项目简历材料：企业智能知识库 RAG 系统 + 求职辅助 Agent

## 简历项目名称

Spring Boot + FastAPI 企业智能知识库 RAG 问答系统 + 求职辅助 Agent

## 一句话描述

基于 Spring Boot、FastAPI、MySQL、Qdrant 和通义千问构建企业知识库问答系统，并扩展求职辅助 Agent。系统支持 PDF 文档入库、RAG 问答、Rerank 重排、引用溯源、拒答、知识库权限控制、聊天会话持久化、简历与岗位 JD 匹配分析、求职分析历史保存和前端演示。

## 推荐简历写法

项目描述：

```text
基于 Spring Boot + FastAPI + MySQL + Qdrant + 通义千问实现企业知识库 RAG 问答系统，并扩展求职辅助 Agent。Spring Boot 负责知识库、文档状态、权限校验、聊天会话、聊天消息和求职分析任务持久化；FastAPI 负责 PDF 解析、Chunk 切分、Embedding、向量检索、qwen3-rerank、大模型问答和求职分析 Prompt 编排；MySQL 保存业务数据，Qdrant 保存文本片段向量。项目支持指定文档问答、引用来源、无依据拒答、重复文档检测、简历与岗位 JD 匹配分析、历史查询、前端演示和自动化评测。
```

职责与亮点：

```text
- 设计 Spring Boot + FastAPI 双后端架构，将业务管理和 AI 能力解耦：Spring Boot 承担业务流程，FastAPI 承担模型调用和 RAG 链路。
- 实现 PDF 文档入库流程：Spring Boot 接收上传文件并保存文档状态，FastAPI 完成解析、切分、Embedding 和 Qdrant 向量写入，处理完成后回写 AVAILABLE / FAILED。
- 使用 MySQL 持久化知识库、文档、聊天会话和聊天消息，替换早期内存存储，使刷新页面和重启服务后数据仍可恢复。
- 实现 SHA-256 重复文档检测，同一知识库内重复 PDF 可直接复用已有文档，避免重复解析和重复向量入库。
- 实现 owner / department 访问控制，限制无权限用户查看文档、创建会话、查看消息和发起提问。
- 实现指定文档 RAG 问答，Spring Boot 校验 documentId 是否属于当前会话的知识库，避免跨知识库越权检索。
- 在 FastAPI 中实现无 Rerank 与 Rerank 两条链路，保留 vector_score 并新增 rerank_score，通过 rerank_min_score 控制拒答。
- 建立 30 条评测集，覆盖普通问答和拒答题，评估 Hit@K、拒答准确率、答案关键词召回、引用有效率、引用支持率、fallback 和 Rerank 耗时。
- 提供静态前端演示页，支持创建知识库、上传 PDF、创建会话、提问、查看引用来源、切换用户身份和权限验证。
- 扩展求职辅助 Agent：FastAPI 负责构建求职分析 Prompt、调用通义千问并解析 JSON；Spring Boot 提供 `POST /api/job-agent/analyze`、`GET /api/job-agent/tasks` 和 `GET /api/job-agent/tasks/{taskId}`，保存、查询求职分析历史并校验详情访问权限。
- 使用 pytest 和 JUnit 固化回归测试，覆盖 FastAPI 核心链路、评测工具函数、Spring Boot 权限边界、聊天流程、文档重复检测和静态页面。
- 使用 Docker Compose 管理 MySQL、Qdrant 和 FastAPI 容器，配合 .env.example、启动文档和新电脑恢复指南提升项目可复现性。
```

## 推荐技术栈写法

```text
Java 21, Spring Boot, Spring Data JPA, MySQL, Python, FastAPI, Pydantic, Qdrant, DashScope, 通义千问 Embedding/Chat, qwen3-rerank, pytest, JUnit, Docker, Docker Compose, HTML/CSS/JavaScript
```

## 项目架构

```text
浏览器前端
-> Spring Boot 业务后端
-> MySQL 保存业务数据
-> FastAPI AI 服务
-> Qdrant 保存向量
-> 通义千问 Embedding / Rerank / Chat
```

## 可量化结果

当前测试集：

```text
30 条评测用例：22 条普通问答，8 条拒答题。
```

自动化测试：

```text
FastAPI pytest：72 passed
Spring Boot Maven test：27 passed
```

Rerank 阈值实验结论：

```text
candidate_k=6、rerank_top_k=3 时，rerank_min_score=0.75 和 0.78 都能保持拒答准确率 1.0；
最终优先推荐 0.75，因为它在保证拒答质量的同时更宽松，未来更不容易误拒正常问题。
```

## 面试时可以强调的能力

```text
1. AI 应用能力：Embedding、向量检索、Rerank、Prompt、拒答、引用溯源。
2. 后端工程能力：Spring Boot 分层、JPA、MySQL、权限校验、异常处理、测试。
3. 系统集成能力：Spring Boot 调 FastAPI，业务数据和向量数据分离，并把 AI 结果落入 MySQL 形成历史记录。
4. 质量意识：不是只看“能回答”，而是有评测集、指标和参数对比。
5. 工程可复现：Docker Compose、.env.example、启动文档、新电脑恢复指南。
```

## 适合面试的项目介绍

```text
我做了一个企业知识库 RAG 问答系统，整体分成 Spring Boot 业务后端和 FastAPI AI 服务两部分。Spring Boot 负责知识库、文档状态、权限控制、聊天会话和消息持久化，FastAPI 负责 PDF 解析、文本切分、Embedding、Qdrant 检索、Rerank 和大模型回答。

文档上传后，系统会先在 Spring Boot 中保存文档记录并计算文件 Hash，避免重复入库；然后调用 FastAPI 完成解析、切分、向量化并写入 Qdrant。用户提问时，Spring Boot 会先校验用户是否有权限访问当前知识库，并校验 documentId 是否属于当前会话的知识库，再调用 FastAPI 进行指定文档 RAG 问答。

为了减少幻觉，我实现了引用来源、无依据拒答和 qwen3-rerank 重排，并建立了 30 条评测集，对回答通过率、拒答准确率、引用有效率、引用支持率和 Rerank 参数进行对比。项目还扩展了求职辅助 Agent，支持简历与岗位 JD 匹配分析、结果持久化和历史查询，并提供前端演示页和自动化测试，方便完整展示从文档上传到问答、权限控制、求职分析和历史查询的流程。
```

# 一周完整项目冲刺计划

## 目标

用 7 天左右，把当前项目一升级成一个完整的简历项目：

```text
Spring Boot + FastAPI + Qdrant + 通义千问企业智能知识库与求职辅助 Agent
```

最终项目不是三个互相割裂的小 Demo，而是一个完整系统：

```text
Vue 或 API 调试界面
-> Spring Boot 业务后端
   -> 用户、权限、知识库、文档、聊天记录、求职任务
   -> MySQL / Redis
   -> 调用 FastAPI AI 服务
      -> PDF 解析、Embedding、Qdrant、RAG、Rerank、Hybrid 检索
      -> 求职辅助 Agent 能力
```

项目一已经作为 FastAPI AI 服务完成，接下来主要做：

1. Spring Boot 业务服务。
2. FastAPI 与 Spring Boot 的 HTTP 接入。
3. 企业知识库业务闭环。
4. 求职辅助 Agent MVP。
5. 简历、演示、面试材料整合。

## 一周内的完成标准

一周目标不是做一个商业级系统，而是做出一个能放简历、能演示、能讲清楚的 MVP。

必须完成：

- Spring Boot 项目可启动。
- Spring Boot 能调用当前 FastAPI RAG 服务。
- 有用户、知识库、文档、聊天会话的核心业务模型。
- 能上传文档或登记文档，并触发 FastAPI 入库。
- 能通过 Spring Boot 发起 RAG 问答。
- 能保存聊天记录和引用来源。
- 能演示求职辅助 Agent MVP：输入简历文本和岗位 JD，输出匹配分析、差距建议、面试题。
- 有统一接口文档、项目说明、简历写法和面试讲解。

可以简化：

- 前端可以先不做完整 Vue 页面，用 Swagger/Postman 演示。
- 权限先做基础版：用户、管理员、知识库归属。
- 文档解析仍由 FastAPI 负责，Spring Boot 只负责编排和状态。
- Agent 先做工作流 MVP，不追求复杂 LangGraph。
- MySQL 表结构先满足演示，不追求复杂审计字段。
- Redis 可以先用于限流或预留配置，不强行做复杂缓存。

## 新学习协作规则

### 你重点学习的内容

这些会影响面试表达和工作能力，需要你理解：

- 为什么要把 Spring Boot 和 FastAPI 拆成两个服务。
- Spring Boot 怎么通过 HTTP 调用 FastAPI。
- Java DTO、Controller、Service、Client 分别负责什么。
- 文档状态为什么需要 `UPLOADED / PROCESSING / AVAILABLE / FAILED`。
- 知识库、文档、会话、消息之间的关系。
- RAG 请求和响应字段为什么这样设计。
- Agent 工作流为什么要拆成“简历解析、JD 分析、匹配度、建议、面试题”。
- 超时、失败、重试、fallback 在企业项目里为什么重要。
- 简历里如何讲清楚技术亮点和取舍。

### 我直接帮你完成的内容

这些属于繁琐工程活，工作中通常也可以高效交给 AI 处理，你负责看懂：

- Spring Boot 基础目录和样板代码。
- DTO、VO、枚举、异常响应、基础 CRUD。
- HTTP Client 封装。
- 详细注释和文档整理。
- Mock 测试和接口示例。
- README、面试材料、演示脚本。
- 重复性的字段映射和 JSON 示例。

### 每个关键任务的教学方式

每个核心节点都按这个节奏：

```text
先讲目标
-> 讲输入输出
-> 讲调用流程
-> 讲关键字段
-> 我直接实现繁琐代码
-> 你看代码并用自己的话解释
-> 我 Review 和补测试
-> 沉淀面试表达
```

## 最终项目定位

简历项目名称建议：

```text
企业智能知识库与求职辅助 Agent 系统
```

一句话描述：

```text
基于 Spring Boot + FastAPI + Qdrant + 通义千问构建企业智能知识库系统，支持文档入库、RAG 问答、Rerank、引用溯源、聊天记录，并扩展求职辅助 Agent，实现简历与岗位 JD 匹配分析、差距建议和面试题生成。
```

## 总体架构

```text
用户 / Swagger / 后续 Vue
        |
        v
Spring Boot 业务服务
  - 用户与权限
  - 知识库管理
  - 文档状态
  - 聊天会话
  - 求职任务
  - MySQL / Redis
        |
        | HTTP
        v
FastAPI AI 服务
  - PDF 解析
  - Chunk 切分
  - Embedding
  - Qdrant
  - Vector / Hybrid 检索
  - qwen3-rerank
  - RAG 生成
  - 评测脚本
```

## 服务边界

### Spring Boot 负责

- 用户、角色、权限。
- 知识库和文档业务数据。
- 文档状态流转。
- 聊天会话和消息记录。
- 调用 FastAPI。
- 保存 RAG 回答和引用来源。
- 求职辅助 Agent 的业务入口。
- MySQL、Redis、统一异常、接口鉴权。

### FastAPI 负责

- PDF 解析。
- 文本切分。
- Embedding。
- Qdrant 向量入库。
- 检索、Hybrid 检索、Rerank。
- Prompt 构建。
- 调用通义千问。
- 返回答案、引用来源、vector_score、rerank_score。

### 为什么这样拆

面试表达：

```text
Spring Boot 更适合承载企业业务系统，比如用户、权限、知识库、聊天记录和数据库事务；FastAPI 更适合承载 AI 能力，比如文档解析、Embedding、向量检索、Rerank 和模型调用。两个服务通过 HTTP 解耦，Java 业务系统不需要关心底层 RAG 实现，Python AI 服务也不需要处理复杂权限和业务状态。
```

## 一周任务安排

### Day 1：Spring Boot 项目骨架 + FastAPI 接口契约

目标：

- 明确 Spring Boot 调 FastAPI 的接口字段。
- 创建 Spring Boot 基础项目结构。
- 完成配置、统一响应、统一异常。
- 写出 FastAPI Client 的基本封装。

交付：

- `controller / service / client / dto / entity / enums` 分层。
- `FastApiRagClient`。
- `/api/health`。
- 接口契约文档。

你要理解：

- Controller、Service、Client 的区别。
- 为什么不在 Controller 里直接写 HTTP 调用。
- DTO 是什么，为什么要和数据库 Entity 分开。

### Day 2：知识库与文档管理

目标：

- 实现知识库、文档的基础业务模型。
- Spring Boot 接收文档上传或文档信息。
- 调用 FastAPI `/documents/index` 完成入库。
- 保存文档状态。

交付：

- KnowledgeBase 表。
- Document 表。
- 文档状态枚举。
- 上传文档接口。
- 文档列表接口。

你要理解：

- 为什么文档需要状态。
- Spring Boot 为什么保存业务元数据，FastAPI 为什么保存向量数据。
- document_id 如何在两个服务之间传递。

### Day 3：聊天会话 + RAG 问答接入

目标：

- Spring Boot 提供聊天接口。
- 调用 FastAPI `/rag/chat/rerank`。
- 保存用户问题、AI 回答、引用来源、耗时和 retrieval_mode。

交付：

- ChatSession 表。
- ChatMessage 表。
- `/api/chat/sessions`。
- `/api/chat/ask`。
- RAG 响应保存。

你要理解：

- 为什么聊天记录由 Spring Boot 保存。
- sources 为什么要保存 JSON。
- FastAPI 返回的 `retrieval_mode`、`rerank_error` 有什么用。

### Day 4：权限、限流和工程化补齐

目标：

- 做基础用户和角色。
- 知识库按 owner 或 department 做隔离。
- Redis 先做简单限流或预留。
- 补统一异常、日志、配置注释。

交付：

- User / Role 简化模型。
- 基础鉴权或模拟用户上下文。
- 简单限流。
- 错误码说明。

你要理解：

- 企业知识库为什么必须做权限。
- 为什么限流可以保护模型成本。
- 真实项目中 Redis 能用于哪些场景。

### Day 5：求职辅助 Agent MVP

目标：

- 把项目三合进完整系统。
- 输入简历文本和岗位 JD。
- 输出匹配度、优势、差距、学习建议、面试题。

交付：

- JobAnalysisTask 表。
- `/api/job-agent/analyze`。
- 简历/JD 分析 Prompt。
- 结构化 JSON 响应。

你要理解：

- Agent MVP 不一定一开始就要复杂框架。
- 为什么可以先用“固定工作流 + LLM”实现。
- Tool Calling / LangGraph 后续可以如何升级。

### Day 6：联调、测试和演示脚本

目标：

- 串起完整演示链路。
- 补核心 Mock 测试。
- 写演示步骤。

交付：

- 一套 Postman/Swagger 演示流程。
- 核心服务 Mock 测试。
- 常见失败场景说明。

演示流程：

```text
创建用户
-> 创建知识库
-> 上传 PDF
-> 文档状态变为 AVAILABLE
-> 发起 RAG 问答
-> 查看回答和引用来源
-> 输入简历和 JD
-> 生成匹配分析和面试题
```

### Day 7：简历、README、面试材料最终版

目标：

- 把项目包装成完整求职项目。
- 准备 1 分钟、3 分钟、10 分钟讲法。
- 整理技术亮点和难点。

交付：

- 完整 README。
- 系统架构图。
- 简历项目描述。
- 面试追问手册。
- 演示 Checklist。

## MVP 数据模型

### 用户

```text
User
- id
- username
- role
- department
- created_at
```

### 知识库

```text
KnowledgeBase
- id
- name
- description
- owner_id
- department
- created_at
```

### 文档

```text
Document
- id
- knowledge_base_id
- filename
- file_hash
- status
- chunk_count
- error_message
- created_at
- updated_at
```

### 聊天

```text
ChatSession
- id
- knowledge_base_id
- user_id
- title
- created_at

ChatMessage
- id
- session_id
- role
- content
- sources_json
- retrieval_mode
- elapsed_seconds
- created_at
```

### 求职 Agent

```text
JobAnalysisTask
- id
- user_id
- resume_text
- job_description
- match_score
- result_json
- created_at
```

## FastAPI 接口契约

### 文档入库

```text
POST /documents/index
Content-Type: multipart/form-data
file: PDF 文件
```

响应核心字段：

```json
{
  "document_id": "uuid",
  "filename": "demo.pdf",
  "chunk_count": 12,
  "status": "indexed"
}
```

### RAG 问答

```text
POST /rag/chat/rerank
Content-Type: application/json
```

请求：

```json
{
  "question": "RAG 的英文全称是什么？",
  "candidate_k": 6,
  "rerank_top_k": 3,
  "rerank_min_score": 0.75,
  "retrieval_mode": "hybrid",
  "keyword_limit": 6,
  "document_id": "可选"
}
```

响应：

```json
{
  "question": "RAG 的英文全称是什么？",
  "answer": "RAG 的英文全称是 Retrieval-Augmented Generation[1]。",
  "sources": [
    {
      "document_id": "uuid",
      "filename": "demo.pdf",
      "page_number": 1,
      "chunk_index": 0,
      "text": "...",
      "vector_score": 0.83,
      "rerank_score": 0.91
    }
  ],
  "retrieval_mode": "rerank",
  "rerank_error": null,
  "rerank_elapsed_seconds": 1.2
}
```

## 一周后的简历亮点

```text
- 基于 Spring Boot + FastAPI 设计双服务架构，Java 负责企业业务，Python 负责 RAG 和模型调用。
- 实现知识库、文档、聊天会话和求职分析任务管理，支持文档入库、RAG 问答、引用来源和聊天记录保存。
- FastAPI AI 服务支持 PDF 解析、Embedding、Qdrant 向量检索、Hybrid 检索、qwen3-rerank 和拒答。
- 建立 RAG 评测闭环，对比无 Rerank、Rerank、不同阈值和不同检索模式的效果。
- 扩展求职辅助 Agent MVP，支持简历与岗位 JD 匹配分析、差距建议和面试题生成。
```

## 第一阶段马上开始的任务

下一步从 Day 1 开始：

```text
创建或整理 Spring Boot 项目
-> 设计 Spring Boot 调 FastAPI 的 DTO
-> 写 FastApiRagClient
-> 写第一个联通测试接口
```

第一步要先确认 Spring Boot 项目位置：

```text
方案 A：在当前仓库下新增 springboot-backend/
方案 B：使用你已有的 Spring Boot 项目目录
```

推荐方案 A。原因是这一周要快速集成成一个完整项目，放在同一个仓库里更方便写 README、Docker Compose 和演示脚本。


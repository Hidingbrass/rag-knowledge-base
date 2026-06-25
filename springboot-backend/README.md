# Spring Boot Backend

这是完整项目中的 Java 业务后端。

当前定位：

```text
用户 / Swagger / 后续 Vue
-> Spring Boot 业务服务
   -> 管理知识库、文档状态、后续聊天记录和用户权限
   -> 通过 HTTP 调用 FastAPI AI 服务
      -> PDF 解析、切分、Embedding、Qdrant、RAG、Rerank
```

## 为什么需要 Spring Boot

FastAPI 已经负责 AI 能力：

- PDF 解析
- 文本切分
- Embedding
- Qdrant 向量入库
- Vector / Hybrid 检索
- qwen3-rerank
- Prompt 和模型调用

Spring Boot 负责企业业务：

- 用户、角色、权限
- 知识库和文档状态
- 聊天会话和消息记录
- 调用 FastAPI
- 后续 MySQL / Redis / 限流 / 日志 / 审计

面试表达：

```text
Spring Boot 更适合承载稳定的企业业务系统，FastAPI 更适合承载 AI 能力。
两者通过 HTTP 解耦，Java 后端不用关心底层 RAG 实现，Python 服务也不用处理复杂权限和业务状态。
```

## 当前已完成

- Spring Boot 项目骨架
- 统一响应 `ApiResponse`
- 全局异常处理
- FastAPI 地址和默认 RAG 参数配置
- FastAPI RAG Client
- 健康检查接口
- RAG 问答代理接口
- 知识库创建、查询、列表
- 文档上传入库、状态保存、文档列表
- 内存版 Repository
- Spring Boot 启动测试和核心 Service 单元测试
- Spring Boot -> FastAPI -> Qdrant -> RAG 问答端到端联调通过

阶段 1 联调记录见：

```text
../docs/springboot_fastapi_stage1_integration.md
```

## 当前接口

### 简易联调页面

Spring Boot 启动后可以直接访问：

```text
http://127.0.0.1:8080/index.html
```

这个页面支持创建知识库、上传 PDF、创建会话、发起 RAG 问答和查看聊天记录。

### 健康检查

```text
GET /api/health
```

### 创建知识库

```text
POST /api/knowledge-bases
Content-Type: application/json
```

```json
{
  "name": "研发知识库",
  "description": "保存研发制度和项目文档",
  "ownerId": "user-1",
  "department": "研发部"
}
```

### 查询知识库列表

```text
GET /api/knowledge-bases
```

### 查询单个知识库

```text
GET /api/knowledge-bases/{knowledgeBaseId}
```

### 上传文档并触发 FastAPI 入库

```text
POST /api/knowledge-bases/{knowledgeBaseId}/documents
Content-Type: multipart/form-data
file: PDF 文件
```

Spring Boot 会做：

```text
校验知识库存在
-> 校验 PDF 文件
-> 创建文档记录，状态为 PROCESSING
-> 调用 FastAPI /documents/index
-> 保存 FastAPI 返回的 document_id、chunk_count、file_hash
-> 状态变成 AVAILABLE
```

如果 FastAPI 调用失败，文档状态会变成 `FAILED`。

### 查询知识库下的文档

```text
GET /api/knowledge-bases/{knowledgeBaseId}/documents
```

### RAG 问答代理

```text
POST /api/rag/ask
Content-Type: application/json
```

```json
{
  "question": "RAG 的英文全称是什么？",
  "documentId": null
}
```

Spring Boot 会转换成 FastAPI 需要的请求：

```json
{
  "question": "RAG 的英文全称是什么？",
  "candidate_k": 6,
  "rerank_top_k": 3,
  "rerank_min_score": 0.75,
  "retrieval_mode": "hybrid",
  "keyword_limit": 6,
  "document_id": null
}
```

## 启动与测试

先确保 FastAPI 服务运行在：

```text
http://127.0.0.1:8000
```

运行测试：

```powershell
mvn -s maven-settings.xml test
```

启动 Spring Boot：

```powershell
mvn -s maven-settings.xml spring-boot:run
```

## 当前取舍

现在知识库和文档数据先保存在内存里。

原因：

```text
先把完整业务链路跑通，再接 MySQL。
这样 Controller / Service / Client / DTO 的结构不会变，后续只需要把内存 Repository 替换成 JPA Repository。
```

下一步会进入聊天会话和 RAG 问答记录保存。

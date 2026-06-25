# 阶段 1 联调记录：Spring Boot 接入 FastAPI RAG 服务

## 目标

本阶段目标是跑通第一条完整链路：

```text
创建知识库
-> 上传 PDF
-> Spring Boot 保存文档状态
-> FastAPI 解析、切分、Embedding、写入 Qdrant
-> Spring Boot 保存 FastAPI 返回的 document_id
-> 通过 Spring Boot 发起指定文档 RAG 问答
-> 返回答案、引用来源、vector_score、rerank_score
```

这一步的意义是证明 Java 业务服务和 Python AI 服务已经可以通过 HTTP 协作。

## 服务边界

Spring Boot 负责：

- 知识库管理
- 文档业务记录
- 文档状态流转
- 统一接口响应
- 调用 FastAPI

FastAPI 负责：

- PDF 解析
- 文本切分
- Embedding
- Qdrant 向量入库
- RAG 检索
- qwen3-rerank
- 大模型生成回答

一句话理解：

```text
Spring Boot 管业务状态，FastAPI 管 AI 能力。
```

## 启动顺序

### 1. 启动 Qdrant

在项目根目录执行：

```powershell
docker compose up -d qdrant
```

检查：

```powershell
Invoke-RestMethod http://127.0.0.1:6333/collections
```

### 2. 启动 FastAPI

在项目根目录执行：

```powershell
cd D:\pycharm\rag-knowledge-base
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

正常返回：

```json
{
  "status": "ok"
}
```

### 3. 启动 Spring Boot

新开一个终端：

```powershell
cd D:\pycharm\rag-knowledge-base\springboot-backend
mvn -s maven-settings.xml spring-boot:run
```

检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

## 手动联调步骤

### 1. 创建知识库

```powershell
$body = @{
  name = "测试知识库"
  description = "用于测试 Spring Boot 调 FastAPI 入库"
  ownerId = "user-1"
  department = "研发部"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri http://127.0.0.1:8080/api/knowledge-bases `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

重点记录返回中的知识库 `id`。

示例：

```text
knowledgeBaseId = 377a06af-7c15-478a-9228-b03ab32ae7c3
```

### 2. 上传 PDF 到知识库

```powershell
$kbId = "377a06af-7c15-478a-9228-b03ab32ae7c3"
$filePath = "D:\pycharm\rag-knowledge-base\rag-learning-test-document.pdf"

curl.exe -X POST `
  -F "file=@$filePath;type=application/pdf" `
  "http://127.0.0.1:8080/api/knowledge-bases/$kbId/documents"
```

本次成功返回：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "id": "76db461f-c97b-4364-b3ea-a3d0d27bb600",
    "knowledgeBaseId": "377a06af-7c15-478a-9228-b03ab32ae7c3",
    "fastApiDocumentId": "b4d5644d-3d3b-4a9d-8ae7-f452abc0da83",
    "filename": "rag-learning-test-document.pdf",
    "fileHash": "d625d3277112409077dda02fb89d1ac3cad543b0be8e24eea80664f1e500e5da",
    "status": "AVAILABLE",
    "chunkCount": 6,
    "errorMessage": null
  }
}
```

重点字段：

- `id`：Spring Boot 业务系统里的文档 ID。
- `fastApiDocumentId`：FastAPI/Qdrant 里的文档 ID，后续 RAG 检索会用它限制范围。
- `status`：文档业务状态，本次为 `AVAILABLE`。
- `chunkCount`：FastAPI 切分出的 chunk 数量，本次为 `6`。
- `fileHash`：文件内容 hash，后续可用于重复上传判断。

### 3. 查询知识库下的文档

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/api/knowledge-bases/$kbId/documents"
```

如果能看到刚上传的文档，并且 `status=AVAILABLE`，说明文档入库链路成功。

### 4. 通过 Spring Boot 发起 RAG 问答

建议用 Postman 测试，因为返回 JSON 比 PowerShell 更清晰。

请求：

```text
POST http://127.0.0.1:8080/api/rag/ask
Content-Type: application/json
```

Body：

```json
{
  "question": "RAG 的英文全称是什么？",
  "documentId": "b4d5644d-3d3b-4a9d-8ae7-f452abc0da83"
}
```

本次成功返回的关键结果：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "question": "RAG 的英文全称是什么？",
    "answer": "RAG 的英文全称是 Retrieval-Augmented Generation [1]。",
    "retrieval_mode": "rerank",
    "rerank_error": null,
    "rerank_elapsed_seconds": 2.9137
  }
}
```

第一条引用来源的关键字段：

```json
{
  "filename": "rag-learning-test-document.pdf",
  "document_id": "b4d5644d-3d3b-4a9d-8ae7-f452abc0da83",
  "page_number": 1,
  "chunk_index": 0,
  "vector_score": 0.8332,
  "rerank_score": 0.9491
}
```

这说明：

```text
FastAPI 确实从刚才上传的 PDF 中检索到了相关 chunk，
Rerank 也把最支持答案的片段排到了前面。
```

## 关键理解

### 为什么要保存文档状态

文档上传不是瞬时完成的，它包含：

```text
PDF 解析
-> 文本切分
-> Embedding
-> Qdrant 入库
```

所以 Spring Boot 需要保存状态：

- `PROCESSING`：正在处理，前端可以展示处理中。
- `AVAILABLE`：入库成功，可以用于 RAG 问答。
- `FAILED`：入库失败，可以展示失败原因。

### 为什么有两个文档 ID

```text
Spring Boot id 管业务。
FastAPI document_id 管检索。
```

`id` 用于 Spring Boot 后续存 MySQL，管理文档列表、状态、权限、聊天记录关联。

`fastApiDocumentId` 用于 FastAPI/Qdrant 检索过滤，问答时传给 FastAPI，限制只查指定文档。

### 为什么问答时要传 documentId

传 `documentId` 是为了限制检索范围。

如果不传，FastAPI 可能会在整个 Qdrant collection 里检索所有文档，导致不同知识库或不同用户的内容串用。

面试表达：

```text
Spring Boot 在调用 FastAPI RAG 问答时会传入 documentId，用来限制向量检索范围。
否则 FastAPI 可能会在全部文档里检索，导致回答引用到其他知识库或其他用户的内容。
这个字段的作用不是保证 HTTP 调用成功，而是保证检索边界和数据隔离。
```

### 为什么上传 PDF 用 multipart/form-data

PDF 是二进制文件，不适合直接放进 JSON。

如果放进 JSON，通常要转成 Base64，体积会变大，也更麻烦。

文件上传一般使用 `multipart/form-data`，它可以在一次 HTTP 请求里携带文件名、文件类型和文件内容。

一句话：

```text
问答参数用 JSON，文件上传用 multipart/form-data。
```

## 当前阶段结论

阶段 1 已经完成真实联调：

```text
Spring Boot 创建知识库成功
Spring Boot 上传 PDF 成功
FastAPI 入库 Qdrant 成功
Spring Boot 保存 fastApiDocumentId 成功
Spring Boot 指定文档 RAG 问答成功
Rerank、引用来源、vector_score、rerank_score 返回正常
```

可以用于简历或面试的总结：

```text
我完成了 Spring Boot 与 FastAPI RAG 服务的端到端联调。Spring Boot 负责知识库、文档状态和统一业务接口，FastAPI 负责 PDF 入库、向量检索、Rerank 和生成回答。文档上传后，Spring Boot 保存 FastAPI 返回的 document_id，并在问答时把它传回 FastAPI，用于限定检索范围，最终返回带引用来源和 rerank_score 的答案。
```

## 下一阶段

下一阶段进入聊天会话和问答记录保存：

```text
创建聊天会话
-> 保存用户问题
-> 调用 FastAPI RAG
-> 保存 AI 回答
-> 保存 sources、retrieval_mode、rerank_elapsed_seconds
```

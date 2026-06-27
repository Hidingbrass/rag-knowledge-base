# Spring Boot Backend

这是完整项目中的 Java 业务后端。

当前定位：

```text
用户 / 静态联调页面 / Postman
-> Spring Boot 业务服务
   -> 管理知识库、文档状态、聊天记录和用户权限
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
- MySQL 持久化
- 后续 Redis / 限流 / 日志 / 审计

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
- 知识库创建、查询、列表
- 文档上传入库、重复检测、状态保存、文档列表
- 聊天会话、聊天消息保存、RAG 问答
- MySQL + Spring Data JPA 持久化
- owner / 同部门用户 / 无权限用户访问控制
- 文档归属校验：提问时 `documentId` 必须属于当前会话的知识库
- 旧版 `/api/rag/ask` 直连接口已停用，避免绕过权限校验
- Spring Boot 启动测试、Service 测试、Controller 权限测试
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

面试演示步骤见：

```text
../docs/day3_frontend_demo_script.md
```

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
POST /api/knowledge-bases/{knowledgeBaseId}/documents?userId=user-1&department=研发部
Content-Type: multipart/form-data
file: PDF 文件
```

Spring Boot 会做：

```text
校验当前用户是否能访问知识库
-> 校验 PDF 文件
-> 计算文件 SHA-256
-> 在当前知识库内做重复上传检测
-> 创建文档记录，状态为 PROCESSING
-> 调用 FastAPI /documents/index
-> 保存 FastAPI 返回的 document_id、chunk_count、file_hash
-> 状态变成 AVAILABLE
```

如果命中重复上传，Spring Boot 会直接复用已有文档记录，不再调用 FastAPI。
如果 FastAPI 调用失败，文档状态会变成 `FAILED`。

### 查询知识库下的文档

```text
GET /api/knowledge-bases/{knowledgeBaseId}/documents?userId=user-1&department=研发部
```

### 创建聊天会话

```text
POST /api/chat/sessions
Content-Type: application/json
```

```json
{
  "knowledgeBaseId": "知识库 UUID",
  "userId": "user-1",
  "department": "研发部",
  "title": "RAG 测试会话"
}
```

### 查询当前用户的会话列表

```text
GET /api/chat/sessions?userId=user-1
```

### 查询会话消息

```text
GET /api/chat/sessions/{sessionId}/messages?userId=user-1&department=研发部
```

### 在会话中发起 RAG 问答

```text
POST /api/chat/sessions/{sessionId}/ask?userId=user-1&department=研发部
Content-Type: application/json
```

```json
{
  "question": "RAG 的英文全称是什么？",
  "documentId": "FastAPI/Qdrant 文档 ID"
}
```

Spring Boot 会先校验：

```text
用户是否能访问 session 所属知识库
-> documentId 是否存在
-> documentId 是否属于当前 session 的知识库
-> 文档状态是否为 AVAILABLE
-> 保存 USER 消息
-> 调用 FastAPI /rag/chat/rerank
-> 保存 ASSISTANT 消息和引用来源
```

### 旧版 RAG 直连接口

```text
POST /api/rag/ask
```

该接口已停用。正式问答必须使用：

```text
POST /api/chat/sessions/{sessionId}/ask
```

原因：旧接口无法校验 session 权限和 documentId 归属，可能绕过知识库权限边界。

## 启动与测试

完整启动说明见：

```text
../docs/startup_guide.md
```

运行测试：

```powershell
mvn -s maven-settings.xml test
```

启动 Spring Boot 前，请确保 MySQL 已在 `127.0.0.1:3307` 启动。

启动命令：

```powershell
mvn -s maven-settings.xml spring-boot:run
```

## 当前权限规则

```text
知识库 owner 可以访问
同部门用户可以访问
其他用户拒绝访问，返回 HTTP 403
```

当前学习版用 `userId + department` 请求参数模拟登录态。
真实项目里这部分会替换成 Spring Security / JWT，从登录用户信息中读取身份和部门。

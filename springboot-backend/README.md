# 知途 AI Spring Boot Backend

这是知途 AI 的 Java 业务后端，负责学习资料、会话、用户身份和求职数据管理。

当前定位：

```text
用户 / 静态联调页面 / Postman
-> Spring Boot 业务服务
   -> 管理知识库、文档状态、聊天记录和用户权限
   -> 通过 Compose 内网 + X-API-Key 调用 FastAPI AI 服务
      -> PDF 解析、切分、Embedding、Qdrant、RAG、Rerank
```

## 为什么需要 Spring Boot

FastAPI 已经负责 AI 能力：

- PDF 解析
- 文本切分
- Embedding
- Qdrant 向量入库
- Dense / Sparse / Hybrid RRF 检索
- qwen3-rerank
- Prompt 和模型调用

Spring Boot 负责应用业务：

- 用户、角色、权限
- 知识库和文档状态
- 聊天会话和消息记录
- 调用 FastAPI
- MySQL 持久化
- Redis AI 调用限流、调用日志、Token/成本聚合和用户审核

面试表达：

```text
Spring Boot 更适合承载稳定的业务系统，FastAPI 更适合承载 AI 能力。
两者通过 HTTP 解耦，Java 后端不用关心底层 RAG 实现，Python 服务也不用处理复杂权限和业务状态。
```

## 当前已完成

- Spring Boot 项目骨架
- 统一响应 `ApiResponse`
- 全局异常处理
- FastAPI 地址和默认 RAG 参数配置
- FastAPI RAG Client
- FastAPI 服务间 API Key；Compose 模式不向宿主机暴露 AI 服务端口
- 健康检查接口
- 知识库创建、查询、列表
- 文档上传入库、重复检测、状态保存、文档列表
- 聊天会话、聊天消息保存、RAG 问答
- MySQL + Spring Data JPA 持久化
- 个人知识库 owner-only 访问控制；同学习方向的其他用户也不能访问
- 文档归属校验：提问时 `documentId` 必须属于当前会话的知识库
- 旧版 `/api/rag/ask` 直连接口已停用，避免绕过权限校验
- Spring Boot 启动测试、Service 测试、Controller 权限测试
- Spring Boot -> FastAPI -> Qdrant -> RAG 问答端到端联调通过
- AI 调用日志记录成功失败、延迟、重试、Token 与估算费用，并提供 24 小时聚合接口
- 聊天、简历、JD 和求职输出支持 AES-256-GCM 字段级加密，旧明文数据保持可读
- 求职生成内容默认待审核，用户本人可通过或驳回并保存审核意见

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

这个页面是 Vite + Vue3 构建的知途 AI，覆盖成长主页、学习资料库、AI 伴学以及岗位分析、
简历优化、面试准备和成果导出的求职工作流。

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
file: PDF、Markdown、DOCX 或 TXT 文件
```

Spring Boot 会做：

```text
校验当前用户是否能访问知识库
-> 校验文件扩展名白名单
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

前端开发、测试与生产构建：

```bash
cd frontend
npm install
npm test -- --run
npm run build
```

`npm run build` 会把生产资源写入 `src/main/resources/static`；Dockerfile 也会在独立 Node
阶段自动执行同样的构建。

启动 Spring Boot 前，请确保 MySQL 已在 `127.0.0.1:3307` 启动。

启动命令：

```powershell
mvn -s maven-settings.xml spring-boot:run
```

## 当前权限规则

```text
知识库 owner 可以访问
非 owner 用户一律拒绝访问，返回 HTTP 403
department 字段仅表示学习方向，不参与权限判断
```

当前默认使用 Spring Security + JWT，从登录 Token 读取用户和部门；客户端传入的 `userId + department` 不能覆盖已认证身份。
只有显式设置 `AUTH_ALLOW_LEGACY_IDENTITY_PARAMETERS=true` 时，旧版联调页才允许使用请求参数模拟身份。

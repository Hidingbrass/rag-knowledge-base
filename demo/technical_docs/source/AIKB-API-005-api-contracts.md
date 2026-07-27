# AIKB 核心 API 契约与错误处理

- 文档编号：AIKB-API-005
- 浏览器入口：Spring Boot 工作台
- 服务间入口：FastAPI

## 1. 认证接口

POST /api/auth/register 用于注册，POST /api/auth/login 用于登录，GET /api/auth/me 用于校验当前
Bearer Token。业务请求使用 Authorization: Bearer accessToken。认证失败返回 401 和统一 JSON 结构，
前端收到后清理 Token 并回到登录界面。

## 2. 知识库与文档接口

POST /api/knowledge-bases 创建知识库，GET /api/knowledge-bases 返回当前用户作为 owner 或同部门可访问
的知识库，GET /api/knowledge-bases/{knowledgeBaseId} 返回有权限的详情。

POST /api/knowledge-bases/{knowledgeBaseId}/documents 使用 multipart/form-data 上传字段名 file 的 PDF。
GET 同一路径返回该知识库的文档列表。业务后端先写 MySQL 状态，再调用 FastAPI 的 POST
/documents/index。FastAPI 还提供 /documents/preview、DELETE /documents/{document_id} 和
GET /documents，但这些是 AI 服务内部文档能力，不应直接作为浏览器权限边界。

## 3. 会话问答接口

POST /api/chat/sessions 创建会话；GET /api/chat/sessions 查询当前用户会话；GET
/api/chat/sessions/{sessionId} 和 /messages 查询会话及消息；POST
/api/chat/sessions/{sessionId}/ask 在指定会话内提问。旧 POST /api/rag/ask 已停用，客户端应使用会话
问答接口。

Spring Boot 调用 POST /rag/chat/rerank 时发送 question、candidate_k、rerank_top_k、
rerank_min_score、retrieval_mode、sparse_limit 和可选 document_id。默认 retrieval_mode 是 hybrid。

## 4. FastAPI RAG 响应

RAG 响应包含 question、answer、sources、retrieval_mode、candidate_retrieval_mode、rerank_error 和
rerank_elapsed_seconds。每个 Source 可以包含 filename、page_number、chunk_index、document_id 以及
vector_score、sparse_score、fusion_score、rerank_score 中当前链路实际产生的字段。

retrieval_mode=rerank 表示 Rerank 正常完成；vector_fallback 表示 Rerank 异常后回退到 Dense 检索。
candidate_retrieval_mode 用于区分候选最初来自 vector 还是 hybrid，不能用 retrieval_mode 代替。

## 5. 参数与错误语义

candidate_k 范围是 1 到 20，sparse_limit 范围是 1 到 20，rerank_top_k 范围是 1 到 10，且
rerank_top_k 不能大于 candidate_k。违反 Pydantic 范围返回参数校验错误；违反两者关系由业务服务返回
400。文件不是 PDF、PDF 无文本或超过 MAX_UPLOAD_SIZE_MB 也返回可读业务错误。

401 表示需要登录或 Token 无效，403 表示用户没有目标资源权限，404 用于不存在的 HTTP 路径，业务对象
不存在通常由统一业务异常结构返回。429 表示 AI 调用超过每分钟或每日限制。服务间调用异常会被 Spring
Boot 包装为业务错误，并记录 AI 调用日志。

## 6. 接口安全边界

FASTAPI_API_KEY 用于 Spring Boot 到 FastAPI 的内部请求头校验。该值留空时关闭内部校验，适合单机开发；
暴露到共享网络时应设置。即使启用了内部 API Key，用户与知识库权限仍然由 Spring Boot 的 JWT 和
Service 校验负责，两者不能互相替代。

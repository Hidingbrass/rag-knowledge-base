# Day 3 前端演示脚本

这个文档用于面试或复盘时演示当前项目。

演示目标不是证明页面多漂亮，而是证明你已经把下面这条链路做成了一个可操作的产品闭环：

```text
静态演示页
-> Spring Boot 业务后端
-> MySQL 保存知识库、文档、会话、消息
-> FastAPI 处理 PDF 入库、RAG、Rerank
-> Qdrant 保存向量数据
-> 页面展示答案、引用来源、权限结果和文档状态
```

## 1. 启动顺序

### 1.1 启动 MySQL 和 Qdrant

在项目根目录执行：

```powershell
cd D:\pycharm\rag-knowledge-base
docker compose up -d
```

检查端口：

```powershell
netstat -ano | findstr ":3307"
netstat -ano | findstr ":6333"
```

你可以这样讲：

```text
MySQL 保存 Spring Boot 侧的业务数据，例如知识库、文档状态、聊天会话和消息。
Qdrant 保存 FastAPI 侧的向量数据，用于语义检索。
```

### 1.2 启动 FastAPI

新开一个 PowerShell：

```powershell
cd D:\pycharm\rag-knowledge-base
.\.venv\Scripts\activate
uvicorn app.main:app --reload --port 8000
```

检查：

```powershell
curl.exe http://127.0.0.1:8000/health
```

你可以这样讲：

```text
FastAPI 是 AI 服务层，负责 PDF 解析、文本切分、Embedding、Qdrant 入库、RAG 检索、Rerank 和调用通义千问。
```

### 1.3 启动 Spring Boot

再新开一个 PowerShell：

```powershell
cd D:\pycharm\rag-knowledge-base\springboot-backend
mvn -s maven-settings.xml spring-boot:run
```

检查：

```powershell
curl.exe http://127.0.0.1:8080/api/health
```

你可以这样讲：

```text
Spring Boot 是业务服务层，负责知识库、权限、文档状态、聊天记录和调用 FastAPI。
```

### 1.4 打开演示页面

浏览器访问：

```text
http://127.0.0.1:8080/index.html
```

如果看不到最新页面，按 `Ctrl + F5` 强制刷新。

## 2. 页面区域怎么讲

页面分成四个核心区域：

```text
演示路线：告诉面试官接下来会演示哪些能力。
从数据库恢复状态：说明页面刷新后可以从 MySQL 恢复知识库、文档和会话。
创建知识库 / 上传 PDF / 创建会话：业务操作入口。
会话问答 / 聊天记录 / 最近一次接口返回：RAG 问答和引用展示。
```

你可以这样开场：

```text
这个前端是一个轻量演示页，主要用于展示后端和 RAG 链路。
它不是核心前端项目，但可以完整演示知识库创建、PDF 入库、权限校验、会话问答和引用来源。
```

## 3. 正常演示流程

### 第一步：检查 Spring Boot

操作：

```text
点击“检查 Spring Boot”
```

预期结果：

```text
状态区显示 Spring Boot 正常。
最近一次接口返回显示 /api/health 的 JSON。
```

你可以这样讲：

```text
这里先确认 Java 业务服务可用。
页面通过 fetch 调用 Spring Boot 的 /api/health，说明静态页和后端接口已经连通。
```

### 第二步：选择 owner 身份

操作：

```text
点击“切换为 owner：user-1 / 研发部”
```

预期结果：

```text
页面重新加载当前用户可见的数据。
状态区 currentUser 显示 user-1 / 研发部。
```

你可以这样讲：

```text
当前学习版用 userId + department 模拟登录用户。
真实项目里这两个字段会来自 JWT 或 Spring Security 的登录上下文。
```

### 第三步：创建或选择知识库

操作：

```text
如果已有知识库，可以直接选择。
如果没有，点击“创建知识库”。
```

预期结果：

```text
knowledgeBaseId 自动填入。
知识库数量增加。
```

你可以这样讲：

```text
知识库数据保存在 MySQL 中。
Spring Boot 负责知识库业务模型，不把这些企业业务关系放到 Qdrant。
```

### 第四步：上传 PDF

操作：

```text
选择 rag-learning-test-document.pdf
点击“上传并入库”
```

预期结果：

```text
如果 FastAPI 正常，文档状态变为 AVAILABLE。
页面显示 fastApiDocumentId。
可提问文档数量增加。
```

你可以这样讲：

```text
上传 PDF 使用 multipart/form-data，因为 PDF 是二进制文件，不适合直接放进 JSON。
Spring Boot 接收文件后先做权限校验、PDF 校验和 SHA-256 重复检测，再调用 FastAPI 入库。
FastAPI 完成解析、切分、Embedding 和 Qdrant 写入后，Spring Boot 保存 fastApiDocumentId 和文档状态。
```

重点字段：

```text
Spring Boot document id：MySQL 业务文档 ID。
fastApiDocumentId：FastAPI/Qdrant 文档 ID，用于 RAG 检索过滤。
status：文档状态，AVAILABLE 才能用于提问。
chunkCount：FastAPI 切出的文本片段数量。
```

### 第五步：演示重复上传

操作：

```text
再次上传同一个 PDF。
```

预期结果：

```text
页面提示“检测到重复 PDF，已复用已有文档”。
FastAPI 不会被重复调用。
```

你可以这样讲：

```text
重复检测放在 Spring Boot 侧做，因为它依赖知识库 ID、文件 hash 和文档状态这些业务数据。
同一个知识库内，如果文件 hash 相同且已有 PROCESSING 或 AVAILABLE 记录，就直接复用。
这样可以避免重复解析、重复向量化，也能节省模型和向量数据库资源。
```

### 第六步：创建会话

操作：

```text
点击“创建会话”。
```

预期结果：

```text
sessionId 自动填入。
当前用户会话数量增加。
```

你可以这样讲：

```text
聊天会话和聊天消息都由 Spring Boot 保存到 MySQL。
这样页面刷新后还能恢复历史会话，也方便后续做审计、统计和用户历史记录。
```

### 第七步：发送 RAG 问题

操作：

```text
点击快捷问题“问：RAG 全称”
点击“发送问题”
```

预期结果：

```text
聊天记录出现 USER 消息和 AI 消息。
AI 消息包含引用来源、page_number、vector_score、rerank_score。
```

你可以这样讲：

```text
提问时必须带 sessionId 和 fastApiDocumentId。
Spring Boot 会先校验用户能访问这个会话所属知识库，再校验 documentId 是否属于当前知识库，并且文档状态是否 AVAILABLE。
校验通过后才会保存用户问题并调用 FastAPI RAG。
```

### 第八步：解释引用来源

操作：

```text
查看 AI 回答下方的“引用来源”。
```

预期结果：

```text
能看到 filename、page、vector_score、rerank_score 和来源片段。
```

你可以这样讲：

```text
引用来源用于降低幻觉风险，也便于用户验证答案来自哪一段文档。
vector_score 表示向量检索阶段的相似度，rerank_score 表示 Rerank 模型重新判断后的相关性。
最终进入 Prompt 的片段是 Rerank 后的结果。
```

## 4. 权限演示流程

### owner 用户

操作：

```text
切换为 user-1 / 研发部。
查看文档、创建会话、查看消息、发起提问。
```

预期结果：

```text
允许访问。
```

### 同部门用户

操作：

```text
切换为 user-2 / 研发部。
查看同一个知识库的文档和消息。
```

预期结果：

```text
允许访问。
```

你可以这样讲：

```text
当前权限规则是 owner 或同部门用户可以访问。
这对应企业知识库中常见的部门资料共享场景。
```

### 无权限用户

操作：

```text
切换为 user-3 / 测试部。
尝试查看文档、创建会话、查看消息或提问。
```

预期结果：

```text
页面显示无权访问。
后端返回 HTTP 403。
```

你可以这样讲：

```text
权限校验必须放在 Spring Boot 后端，而不是只靠前端隐藏按钮。
即使用户手动构造请求，后端仍然会校验知识库 owner、department 和 documentId 归属。
```

## 5. 失败场景怎么讲

### 文档状态是 FAILED

页面现象：

```text
失败文档数量增加。
下一步建议提示先启动 FastAPI 再重新上传 PDF。
fastApiDocumentId 为空，不能提问。
```

原因：

```text
Spring Boot 调 FastAPI 入库失败，例如 FastAPI 没启动、网络不可用或模型服务异常。
```

你可以这样讲：

```text
文档入库是一个跨服务流程，失败时不能假装成功。
Spring Boot 会保留 FAILED 记录和 errorMessage，方便前端展示失败原因，也方便排查问题。
```

### 没有 fastApiDocumentId

页面现象：

```text
documentId 输入框为空。
发送问题前端会提示先选择或上传可用 PDF。
```

原因：

```text
只有 FastAPI 入库成功后，Spring Boot 才能拿到 fastApiDocumentId。
```

你可以这样讲：

```text
Spring Boot 的文档 ID 和 FastAPI 的 document_id 是两个概念。
前者管业务记录，后者管向量检索。
RAG 提问时需要后者来限制检索范围。
```

## 6. 3 分钟项目讲法

可以按这个顺序讲：

```text
这个项目是一个企业智能知识库系统。
整体采用 Spring Boot + FastAPI 双服务架构。
Spring Boot 负责企业业务，包括知识库、文档状态、用户权限、聊天会话和 MySQL 持久化。
FastAPI 负责 AI 能力，包括 PDF 解析、文本切分、Embedding、Qdrant 向量检索、Hybrid 检索、qwen3-rerank 和通义千问生成回答。

用户在页面上传 PDF 后，Spring Boot 会先做权限校验、PDF 校验和文件 hash 重复检测。
如果不是重复文档，就调用 FastAPI 完成向量入库，并把 FastAPI 返回的 document_id 保存到 MySQL。
提问时，Spring Boot 会校验用户是否能访问会话所属知识库，并校验 documentId 是否属于该知识库。
校验通过后才会调用 FastAPI RAG，最终把 AI 回答、引用来源、检索模式和 rerank 耗时保存为聊天记录。

项目里还做了 RAG 评测和参数对比，例如 Hit@K、拒答准确率、引用有效率和 Rerank 阈值对比。
这样不是只把功能跑通，而是能用数据判断检索和生成质量。
```

## 7. 面试常见追问

### 为什么 Spring Boot 不直接做 RAG？

```text
因为 Java 更适合承载企业业务系统，比如权限、事务、用户、知识库、聊天记录；
Python 生态更适合 AI 能力，比如 PDF 解析、Embedding、向量检索、Rerank 和模型调用。
拆成两个服务后，业务和 AI 能力可以独立迭代。
```

### 为什么不能只靠 Qdrant 保存文档？

```text
Qdrant 擅长保存向量和做相似度检索，但不适合保存复杂业务关系。
用户、权限、知识库归属、文档状态、聊天记录更适合放在 MySQL。
```

### 为什么提问时要传 documentId？

```text
documentId 用于限制 FastAPI/Qdrant 的检索范围。
如果不传，可能在所有文档中检索，导致不同知识库的数据串用。
```

### 为什么要做 documentId 归属校验？

```text
只校验用户能访问 session 还不够。
用户可能手动传入其他知识库的 documentId。
所以 Spring Boot 必须查 MySQL，确认这个 fastApiDocumentId 属于当前 session 的知识库。
```

### 为什么要 Rerank？

```text
向量检索负责从大量 chunk 中召回候选片段，但召回结果不一定排序最准确。
Rerank 会对候选片段重新判断和问题的相关性，把最支持答案的片段排到前面。
这样可以提升进入 Prompt 的上下文质量。
```

### 为什么要返回引用来源？

```text
引用来源可以让用户检查答案来自哪份文档、哪一页、哪个片段。
这能降低大模型幻觉带来的风险，也方便做企业知识库场景下的审计和追溯。
```

## 8. Day 3 完成标准

Day 3 可以视为完成，如果满足：

- 页面可以从 MySQL 恢复知识库、文档和会话。
- 页面可以展示当前身份、文档状态和下一步建议。
- 可以上传 PDF 并看到 `AVAILABLE` 或 `FAILED`。
- 可以创建会话并发送 RAG 问题。
- 可以看到聊天记录和引用来源。
- 可以切换 owner、同部门、无权限用户验证权限。
- `/index.html` 和 `/favicon.ico` 有测试保护。

当前代码已经覆盖这些点。

# main.py 工程化分层计划

## 1. 当前问题

当前项目已经完成 PDF 解析、文本切分、Embedding、Qdrant 向量入库、文档管理、向量检索、RAG 问答、拒答、引用、Rerank 和评测脚本，功能链路已经比较完整。

但随着功能增加，`app/main.py` 承担的职责越来越多。它不仅负责创建 FastAPI 应用和定义接口，还包含配置读取、Pydantic 模型、PDF 处理、文本切分、Embedding 调用、Qdrant 操作、RAG Prompt 构建、通义千问调用、Rerank 调用和业务流程编排。

这种结构在学习早期有利于快速理解完整链路，但继续扩展时会带来几个问题：

- 文件过长，不方便阅读和定位问题。
- 接口层、业务层和外部服务调用混在一起。
- RAG、Rerank、Qdrant、Qwen 等逻辑难以单独测试。
- 后续接入 Spring Boot、日志、异常处理和 pytest 时不够清晰。
- 简历项目展示时，工程化程度不足。

因此，下一阶段目标是对 `main.py` 进行渐进式拆分，让项目从学习 Demo 逐步变成结构清晰的后端服务。

## 2. 当前 main.py 职责盘点

当前 `main.py` 中大概率包含以下职责：

### 2.1 应用初始化

- 创建 FastAPI 应用。
- 配置接口路由。
- 配置全局客户端或环境变量。

### 2.2 配置与常量

- 读取 `.env`。
- 获取通义千问 API Key。
- 定义 Qdrant Collection 名称。
- 定义模型名称、默认参数、拒答短语等常量。

### 2.3 Pydantic 模型

- 上传、检索、RAG 问答相关请求模型。
- Rerank RAG 请求模型。
- 文档、检索结果或响应相关结构。

### 2.4 PDF 与文本处理

- PDF 文本解析。
- 按页读取文本。
- 文本切分。
- Chunk 元数据维护，例如 `page_number`、`chunk_index`、`filename`。

### 2.5 Embedding 调用

- 调用通义千问 Embedding。
- 单条或批量生成向量。
- 处理 Embedding 返回结果。

### 2.6 Qdrant 操作

- 创建或检查 Collection。
- 写入 Point。
- 按向量检索。
- 使用 Payload Filter 按 `document_id` 过滤。
- 使用 `scroll` 列出文档。
- 删除指定文档。

### 2.7 文档管理

- 生成 `document_id`。
- 计算 SHA-256。
- 检测重复文档。
- 维护文件名、页码、Chunk 数量等元数据。

### 2.8 RAG 流程

- 检索候选片段。
- 按 `vector_score` 过滤。
- 构建 RAG Prompt。
- 调用通义千问 Chat。
- 返回答案和引用来源。

### 2.9 Rerank 流程

- 调用 qwen3-rerank。
- 根据返回的 `index` 找回原始 Source。
- 保留 `vector_score`。
- 新增 `rerank_score`。
- 根据 `rerank_min_score` 判断是否拒答。
- 统计 Rerank 耗时、错误和 fallback。

### 2.10 API 路由

- PDF 上传接口。
- 文档列表接口。
- 文档删除接口。
- 检索演示接口。
- 无 Rerank RAG 问答接口。
- Rerank RAG 问答接口。

## 3. 目标目录结构

目标不是一次性大重构，而是逐步拆分到以下结构：

```text
app/
├── main.py
├── core/
│   ├── config.py
│   └── exceptions.py
├── schemas/
│   ├── document.py
│   ├── rag.py
│   └── search.py
├── services/
│   ├── pdf_service.py
│   ├── chunk_service.py
│   ├── embedding_service.py
│   ├── qdrant_service.py
│   ├── qwen_service.py
│   ├── rag_service.py
│   └── rerank_service.py
├── api/
│   ├── documents.py
│   ├── search.py
│   └── rag.py
├── evaluation/
│   ├── cases.py
│   ├── evaluate_retrieval.py
│   ├── evaluate_generation.py
│   ├── evaluate_rerank.py
│   ├── evaluate_generation_rerank.py
│   └── evaluate_rerank_params.py
└── tests/
    └── ...
```

最终目标：

- `main.py` 只负责创建 FastAPI app 和注册路由。
- `schemas/` 只保存 Pydantic 请求和响应模型。
- `services/` 保存核心业务逻辑和外部服务调用。
- `api/` 保存接口函数。
- `core/` 保存配置、异常和基础设施。
- `evaluation/` 保存评测脚本。
- `tests/` 保存自动化测试。

## 4. 分层原则

### 4.1 main.py 保持轻量

`main.py` 最终只做三件事：

```text
创建 app
注册 router
启动服务入口
```

它不再直接包含 PDF、Qdrant、RAG、Rerank 等业务细节。

### 4.2 schemas 只定义数据结构

`schemas/` 中只放 Pydantic 模型，例如：

- `RagChatRequest`
- `RerankRagChatRequest`
- `SearchRequest`
- `DocumentInfo`

这些文件不调用 Qdrant，不调用大模型，也不写业务流程。

### 4.3 services 放业务逻辑

`services/` 是项目核心。

例如：

- `pdf_service.py` 负责 PDF 解析。
- `chunk_service.py` 负责文本切分。
- `embedding_service.py` 负责 Embedding。
- `qdrant_service.py` 负责向量数据库操作。
- `qwen_service.py` 负责通义千问 Chat 和 Embedding API。
- `rerank_service.py` 负责 qwen3-rerank。
- `rag_service.py` 负责编排检索、构建 Prompt、拒答和生成。

### 4.4 api 只处理 HTTP 层

`api/` 中的函数只负责：

```text
接收请求
调用 service
返回响应
```

它不应该包含复杂业务逻辑。

### 4.5 evaluation 独立于接口

评测脚本应尽量调用 service 或接口函数，但不要和 API 路由强绑定。后续可以逐步迁移：

```text
app/evaluate_*.py
-> app/evaluation/evaluate_*.py
```

这样评测能力可以独立复用。

## 5. 拆分步骤

为了降低风险，拆分必须分步骤进行。每一步完成后都要运行回归测试，确认原有功能没有被破坏。

### 第一步：抽取 schemas

目标：

把 Pydantic 请求模型从 `main.py` 移动到 `app/schemas/`。

建议文件：

```text
app/schemas/rag.py
app/schemas/search.py
app/schemas/document.py
```

优先移动：

- `RagChatRequest`
- `RerankRagChatRequest`
- 检索相关请求模型
- 文档相关响应模型

当前进度：

- 已新增 `app/schemas/rag.py`
- 已新增 `app/schemas/search.py`
- 已新增 `app/schemas/chat.py`
- 已抽取 `RagChatRequest`
- 已抽取 `RerankRagChatRequest`
- 已抽取 `SearchRequest`
- 已抽取 `ChatMessage`
- 已抽取 `ChatRequest`
- `main.py` 中对应 Pydantic 模型已移除
- `evaluate_generation.py` 已改为从 `app.schemas.rag` 导入 `RagChatRequest`
- `evaluate_generation_rerank.py` 已改为从 `app.schemas.rag` 导入 `RerankRagChatRequest`

验收标准：

- `/rag/chat` 正常。
- `/rag/chat/rerank` 正常。
- `evaluate_generation.py` 正常。
- `evaluate_generation_rerank.py` 正常。
- `evaluate_rerank_params.py` 正常。

### 第二步：抽取 qwen_service

目标：

把通义千问相关调用移动到：

```text
app/services/qwen_service.py
```

包括：

- Chat 调用
- Embedding 调用
- API Key 使用
- 模型名称常量

当前进度：

- 已新增 `app/services/qwen_service.py`
- 已新增 `app/services/__init__.py`
- 已抽取 `create_embedding()`
- 已抽取 `create_embeddings()`
- 已新增 `chat_completion()`
- `/chat`、`/rag/chat`、`/rag/chat/rerank` 已统一通过 `chat_completion()` 调用通义千问 Chat
- `main.py` 已移除 `OpenAI`、`load_dotenv`、`client` 等直接模型客户端依赖

验收标准：

- 上传文档仍能生成 Embedding。
- RAG 问答仍能调用 Chat。
- 无 Rerank 和 Rerank 生成评测仍能运行。

### 第三步：抽取 rerank_service

目标：

把 `rerank_chunks()` 移动到：

```text
app/services/rerank_service.py
```

保留行为：

- 输入 `question`、`sources`、`rerank_top_k`
- 调用 qwen3-rerank
- 根据 `index` 找回原 Source
- 返回带 `rerank_score` 的 Source 列表

当前进度：

- 已新增 `app/services/rerank_service.py`
- 已抽取 `rerank_chunks()`
- `main.py` 已移除 `requests` 直接依赖
- `main.py` 已不再从 `qwen_service` 导入 `api_key`
- Rerank 成功路径已通过 Mock 验证：能够根据 `index` 找回原 Source，并且不污染原始 `sources`
- Rerank 失败路径已通过 Mock 验证：`/rag/chat/rerank` 仍会进入 `vector_fallback`

验收标准：

- `evaluate_rerank.py` 正常。
- `evaluate_generation_rerank.py` 正常。
- Rerank 失败 fallback 行为不变。

### 第四步：抽取 qdrant_service

目标：

把 Qdrant 相关操作移动到：

```text
app/services/qdrant_service.py
```

包括：

- 创建 Collection
- 插入向量
- `search_chunks()`
- 按 `document_id` 删除
- `scroll` 文档列表

当前进度：

- 已新增 `app/services/qdrant_service.py`
- 已抽取 `qdrant_client`
- 已抽取 `COLLECTION_NAME`
- 已抽取 `ensure_collection()`
- 已抽取 `reset_collection()`
- 已抽取 `search_chunks()`
- 已抽取 `find_document_by_hash()`
- 已抽取 `delete_document_points()`
- 已抽取 `list_indexed_documents()`
- 已抽取 `upsert_document_chunks()`
- `evaluate_retrieval.py` 已改为从 `app.services.qdrant_service` 导入 `search_chunks`
- `evaluate_rerank.py` 已改为从 `app.services.qdrant_service` 导入 `search_chunks`
- `search_chunks()` 已通过 Mock 验证：保留 `document_id` 过滤、返回 `vector_score`，并保持 RAG Source 字段结构不变
- `upsert_document_chunks()` 已通过 Mock 验证：能够创建 collection 并批量写入 chunk points
- `delete_document_points()` 与 `list_indexed_documents()` 已通过 Mock 验证
- `/search/demo` 中独立使用的 `score` 字段保持不变

验收标准：

- 上传 PDF 正常。
- 文档列表正常。
- 文档删除正常。
- 向量检索正常。
- RAG 问答正常。

### 第五步：抽取 rag_service

目标：

把 RAG 编排逻辑移动到：

```text
app/services/rag_service.py
```

包括：

- `build_rag_messages()`
- 无 Rerank RAG 流程
- Rerank RAG 流程
- 拒答逻辑
- fallback 逻辑

当前进度：

- 已新增 `app/services/rag_service.py`
- 已抽取 `build_rag_messages()`
- 已抽取 `rag_chat_response()`
- 已抽取 `rag_chat_with_rerank_response()`
- `main.py` 中 `/rag/chat` 已改为调用 `rag_chat_response()`
- `main.py` 中 `/rag/chat/rerank` 已改为调用 `rag_chat_with_rerank_response()`
- Rerank 参数错误通过 `ValueError` 交给 API 层转换为 HTTP 400
- 普通 RAG、Rerank 成功和 Rerank fallback 已通过 Mock 验证

验收标准：

- `/rag/chat` 行为不变。
- `/rag/chat/rerank` 行为不变。
- 无 Rerank 生成基线仍可运行。
- Rerank 生成评测仍可运行。

### 第六步：抽取 api 路由

目标：

把接口函数移动到：

```text
app/api/documents.py
app/api/search.py
app/api/rag.py
```

`main.py` 中只保留：

```python
from fastapi import FastAPI

from app.api.documents import router as documents_router
from app.api.search import router as search_router
from app.api.rag import router as rag_router

app = FastAPI()

app.include_router(documents_router)
app.include_router(search_router)
app.include_router(rag_router)
```

当前进度：

- 已新增 `app/api/__init__.py`
- 已新增 `app/api/health.py`
- 已新增 `app/api/chat.py`
- 已新增 `app/api/documents.py`
- 已新增 `app/api/search.py`
- 已新增 `app/api/rag.py`
- 已新增 `app/services/document_service.py`
- 已新增 `app/services/chat_service.py`
- 已新增 `app/services/search_service.py`
- `main.py` 已精简为 FastAPI app 创建和 router 注册
- `/health`、`/qdrant/health`、`/chat`、`/documents/preview`、`/documents/index`、`/documents/{document_id}`、`/documents`、`/search/demo`、`/embedding/test`、`/rag/chat`、`/rag/chat/rerank` 路径已通过路由注册检查
- `evaluate_generation.py` 和 `evaluate_generation_rerank.py` 已改为直接调用 `rag_service`，不再从 `app.main` 导入业务函数

验收标准：

- Swagger 中接口仍存在。
- 所有路径保持不变。
- 旧评测脚本仍可运行。

### 第七步：迁移 evaluation

目标：

把评测脚本统一移动到：

```text
app/evaluation/
```

注意：

这一步不急。等核心服务分层稳定后再做，避免同时修改 import 路径导致排错复杂。

### 已完成：配置管理

目标：

把模型名称、Qdrant 地址、Collection 名称、请求超时和 RAG/Rerank 默认参数集中到：

```text
app/core/config.py
```

当前进度：

- 已新增 `app/core/__init__.py`
- 已新增 `app/core/config.py`
- 已通过 `Settings` 集中管理 `DASHSCOPE_API_KEY`、`DASHSCOPE_BASE_URL`
- 已集中管理 `QDRANT_URL`、`QDRANT_COLLECTION_NAME`
- 已集中管理 `QWEN_EMBEDDING_MODEL`、`QWEN_EMBEDDING_DIMENSIONS`、`QWEN_CHAT_MODEL`
- 已集中管理 `QWEN_RERANK_MODEL`、`QWEN_RERANK_API_URL`
- 已集中管理 `MODEL_REQUEST_TIMEOUT_SECONDS`
- 已集中管理普通 RAG 默认参数：`RAG_DEFAULT_TOP_K`、`RAG_DEFAULT_MIN_SCORE`
- 已集中管理 Rerank 默认参数：`RAG_DEFAULT_CANDIDATE_K`、`RAG_DEFAULT_RERANK_TOP_K`、`RAG_DEFAULT_RERANK_MIN_SCORE`、`RAG_DEFAULT_FALLBACK_MIN_SCORE`
- 已集中管理评测慢调用阈值：`RAG_SLOW_RERANK_THRESHOLD`
- `qwen_service.py`、`qdrant_service.py`、`rerank_service.py`、`schemas/rag.py` 和评测脚本已改为读取 `settings`

验收标准：

- 修改默认参数时优先修改 `.env` 或 `app/core/config.py`
- 接口默认参数和评测脚本默认参数保持一致
- Qwen、Qdrant、Rerank 不再各自硬编码模型名、地址和超时时间

### 已完成：日志配置

目标：

把项目日志配置集中到：

```text
app/core/logging.py
```

当前进度：

- 已新增 `setup_logging()`：统一初始化日志格式和日志级别
- 已新增 `get_logger(__name__)`：各模块按自己的模块名输出日志
- 已新增 `LOG_LEVEL` 配置项，默认 `INFO`
- `main.py` 已在创建 app 时初始化日志
- `document_service.py` 已记录 PDF 预览、文档入库、重复文档和入库完成
- `qdrant_service.py` 已记录 collection 创建、重建、检索、删除和 chunks 写入
- `rag_service.py` 已记录普通 RAG 检索、拒答、生成、Rerank 耗时和 fallback
- `rerank_service.py` 已记录 Rerank 跳过和 API 返回结果数量

验收标准：

- 服务运行时能看到关键链路日志
- 出现 Rerank fallback 时能看到错误和耗时
- 出现业务异常或未处理异常时能看到统一日志

### 已完成：统一异常

目标：

把业务异常定义和 FastAPI 异常处理集中到：

```text
app/core/exceptions.py
```

当前进度：

- 已新增 `AppError` 作为项目业务异常基类
- 已新增 `BadRequestError`，用于参数错误、文件类型错误、PDF 解析失败等 HTTP 400 场景
- 已新增 `DuplicateDocumentError`，用于重复文档 HTTP 409 场景
- 已新增 `app_error_handler()`，统一把业务异常转成 JSON 响应
- 已新增 `unhandled_exception_handler()`，统一处理未预期异常并记录堆栈
- 已新增 `register_exception_handlers(app)`，在 `main.py` 中注册全局异常处理器
- `api/chat.py`、`api/rag.py`、`api/search.py`、`api/health.py`、`api/documents.py` 已移除重复 `try/except`

统一错误响应示例：

```json
{
  "error_code": "BAD_REQUEST",
  "message": "rerank_top_k 不能大于 candidate_k"
}
```

验收标准：

- `rerank_top_k > candidate_k` 返回 HTTP 400
- 非 PDF 上传返回 HTTP 400
- 重复文档返回 HTTP 409
- 未预期异常返回 HTTP 500，并由日志记录堆栈

### 已完成：pytest 自动化测试

目标：

把当前依赖手动检查的 Mock 回归固化到：

```text
tests/
```

当前进度：

- 已新增 `pytest.ini`
- 已新增 `requirements.txt`，记录 pytest 和项目运行依赖
- 已新增 `tests/test_routes_and_errors.py`
- 已新增 `tests/test_config_defaults.py`
- 已新增 `tests/test_evaluation_cases.py`
- 已新增 `tests/test_rerank_service.py`
- 已新增 `tests/test_qdrant_service.py`
- 已新增 `tests/test_rag_service.py`

当前覆盖范围：

- 路由注册完整性
- `rerank_top_k > candidate_k` 返回统一 HTTP 400
- 非 PDF 上传返回统一 HTTP 400
- 普通 RAG 和 Rerank RAG 默认参数来自 `settings`
- 测试集结构保持 30 条、22 普通题、8 拒答题
- `rerank_chunks()` 根据 Rerank API 返回的 `index` 回填原 Source
- `rerank_chunks()` 不污染原始 `sources`
- `search_chunks()` 返回 `vector_score`，不返回旧 `score`
- `search_chunks()` 能传递 `document_id` 过滤条件
- 普通 RAG 能按 `min_score` 过滤 source
- 普通 RAG 无可用 source 时拒答
- Rerank API 失败时进入 `vector_fallback`

运行命令：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

当前结果：

```text
67 passed
```

### 已完成：Docker Compose 和启动文档

目标：

让项目具备可复现的本地启动和 Docker Compose 启动方式。

当前进度：

- 已新增 `Dockerfile`，用于构建 FastAPI 服务镜像
- 已新增 `docker-compose.yml`，同时启动 FastAPI `api` 服务和 Qdrant 服务
- 已新增 `.dockerignore`，避免把 `.venv`、`.env`、缓存文件和测试 PDF 打进镜像
- 已新增 `.env.example`，提供环境变量模板，不包含真实 API Key
- 已新增 `docs/startup_guide.md`，记录本地启动、Docker Compose 启动、测试、接口验证和常见问题
- 已更新 `README.md`，加入启动说明入口和 Docker Compose 运行命令

关键设计：

- 本地 Python 运行时，`QDRANT_URL=http://127.0.0.1:6333`
- Docker Compose 运行时，`QDRANT_URL=http://qdrant:6333`
- Docker Compose 使用 `env_file: .env` 读取真实密钥
- `docker-compose.yml` 中通过 `environment` 覆盖容器内的 Qdrant 地址

验收结果：

```powershell
docker compose config
.\.venv\Scripts\python.exe -m pytest
```

结果：

```text
docker compose config 通过
67 passed
```

## 6. 风险与回归测试

### 6.1 主要风险

拆分过程中容易出现以下问题：

- import 路径错误。
- 循环导入。
- Pydantic 模型路径变更导致评测脚本失效。
- 评测脚本仍从 `app.main` 导入旧函数。
- 全局客户端移动后初始化顺序错误。
- Rerank fallback 行为被改坏。
- `/search/demo` 中独立使用的 `score` 被误改。

### 6.2 回归测试清单

每完成一步拆分，至少运行：

```powershell
.\.venv\Scripts\python.exe -m pytest
python -m py_compile app/main.py
python -m app.evaluation.evaluate_generation
python -m app.evaluation.evaluate_generation --top-k 5 --min-score 0.5
python -m app.evaluation.evaluate_generation_rerank --mode single
python -m app.evaluation.evaluate_generation_rerank --mode comparison
python -m app.evaluation.evaluate_rerank_params
```

如果改动涉及检索或 Rerank，还需要运行：

```powershell
python -m app.evaluation.evaluate_retrieval
python -m app.evaluation.evaluate_rerank
```

### 6.3 Mock 测试重点

应优先补充或保留以下 Mock 测试：

- 空候选时拒答。
- 低 `rerank_score` 时拒答。
- `rerank_top_k > candidate_k` 时返回参数错误。
- Rerank API 抛异常时走 `vector_fallback`。
- fallback 后仍能构建 Prompt 并生成答案。
- `document_id` 能正确传递到检索层。
- `vector_score` 和 `rerank_score` 不互相覆盖。

## 7. 当前建议

下一步不要一次性大规模重构，`schemas`、`services`、`api`、`core/config`、`core/logging`、`core/exceptions`、`tests` 和 Docker Compose 启动文件已完成第一轮整理。接下来可以继续补强业务效果和项目表达：

```text
重跑 30 条测试集评测、Chunk 级支持评测、evaluation 目录迁移、接入 Spring Boot
```

原因：

- 当前功能拆分已经形成 api / schemas / services 三层结构。
- 配置管理已经收口模型名称、Qdrant 地址、Collection 名称和默认阈值。
- 日志、统一异常和 pytest 已经完成，项目具备基本工程质量保障。
- Docker Compose 和启动文档已经让 Qdrant、FastAPI、环境变量和启动命令更容易复现。
- 测试集已经扩展到 30 条，下一步需要在 Qdrant 正常运行后重跑检索、Rerank 和生成评测。

当前 30 条测试集已完成 Rerank 生成评测和阈值对比。推荐配置已更新为 `candidate_k=6`、`rerank_top_k=3`、`rerank_min_score=0.75`。评测脚本已迁移到独立 `app/evaluation/` 目录。下一阶段建议优先补充评测结果 JSON 保存能力，再继续增强 Chunk 级支持评测。

后续任务流程：

1. 保持评测函数返回结构化结果，由 `__main__` 负责打印或保存。
2. 增加 JSON 结果保存能力，方便多次参数实验对比。
3. 继续增强 Chunk 级支持评测，降低关键词匹配误判。
4. 准备 Spring Boot 接入 FastAPI RAG 服务的接口契约。

# RAG Knowledge Base

一个基于 FastAPI、Qdrant 和通义千问的本地知识库 RAG 学习项目。项目从底层实现 PDF 解析、文本切分、Embedding 入库、向量检索、RAG 问答、引用来源、拒答、Rerank 和质量评测，用于理解企业知识库问答系统的核心链路。

当前项目重点不是直接使用 LangChain 等框架完成黑盒调用，而是手动拆解 RAG 的关键步骤，建立可评测、可对比、可解释的优化闭环。

## 简历与面试材料

如果用于求职或面试，建议优先阅读：

- [项目一简历材料](docs/resume_project_one.md)
- [项目一面试讲解手册](docs/interview_project_one.md)
- [一周完整项目冲刺计划](docs/one_week_full_project_plan.md)
- [RAG + Rerank 实验报告](docs/rag_rerank_experiment_report.md)
- [启动与部署说明](docs/startup_guide.md)

## 技术栈

- Python
- FastAPI
- Qdrant
- 通义千问 Embedding
- 通义千问 Chat
- qwen3-rerank
- PDF 文本解析
- Docker
- Pydantic

## 核心功能

- PDF 文档解析
- 文本切分与 Chunk 管理
- 通义千问 Embedding 向量化
- Qdrant 向量入库
- 多文档管理
- 基于 `document_id` 的指定文档检索
- SHA-256 重复文档检测
- RAG 问答
- 无相关资料时拒答
- 回答引用来源
- Qdrant 向量检索评测
- RAG 生成质量评测
- qwen3-rerank 重排序
- Hybrid 检索：向量检索 + 关键词检索
- vector/hybrid 候选检索模式对比
- Rerank 参数对比实验
- fallback、耗时和慢调用统计

## 系统流程

文档入库流程：

```text
PDF 文档
-> 文本解析
-> 文本切分
-> Embedding 向量化
-> 写入 Qdrant
```

无 Rerank 问答流程：

```text
用户问题
-> Embedding 向量化
-> Qdrant 向量检索
-> min_score 过滤
-> 构建 Prompt
-> 调用通义千问
-> 返回答案和引用来源
```

Rerank 问答流程：

```text
用户问题
-> Qdrant 向量检索扩大候选集
-> qwen3-rerank 重排序
-> 保留 rerank_top_k 个片段
-> 根据 rerank_min_score 判断是否拒答
-> 构建 Prompt
-> 调用通义千问
-> 返回答案、引用来源、vector_score 和 rerank_score
```

## RAG + Rerank 方案

原始向量检索只根据语义相似度召回片段，可能把语义相近但不能直接回答问题的内容排在前面。Rerank 的作用是对候选片段进行二次排序，让更能支持答案的片段进入 Prompt。

当前 Rerank 设计：

- 向量检索阶段扩大候选集，例如 `candidate_k=6`
- Rerank 前不使用原向量阈值过滤候选
- 根据 qwen3-rerank 返回的 `index` 找回原始 Source
- 保留 `vector_score`
- 新增 `rerank_score`
- Rerank 后保留 `rerank_top_k=3`
- 使用 `rerank_min_score=0.75` 判断是否拒答

当前推荐参数：

```text
candidate_k = 6
rerank_top_k = 3
rerank_min_score = 0.75
```

推荐理由：

在历史 18 条测试集上，该配置达到回答通过率、拒答准确率、引用有效率和引用支持率均为 1.0。与更大的候选集相比，在质量和稳定性相同的情况下，它使用更少候选片段和更短 Prompt，成本更低，噪声更少。当前测试集已扩展到 30 条，推荐参数需要在 Qdrant 正常运行后重新评测确认。

## 评测结果

当前测试集已扩展到 30 条：

- 22 条普通问答题
- 8 条拒答题

下面表格保留历史基线结果；当前 30 条测试集上的最新 Rerank 阈值结论见下方“当前最新进度”。

无 Rerank 检索基线：

| 指标 | 结果 |
|---|---:|
| Hit@3 | 0.8571 |
| 检索拒答准确率 | 0.5 |

无 Rerank 生成基线：

| 指标 | 结果 |
|---|---:|
| 回答通过率 | 0.8571 |
| 生成拒答准确率 | 0.75 |
| 引用有效率 | 0.9286 |
| 引用支持率 | 0.8571 |

Rerank 检索结果：

| 指标 | 结果 |
|---|---:|
| 候选 Hit@6 | 1.0 |
| Rerank Hit@3 | 1.0 |

Rerank 生成结果：

| 指标 | 结果 |
|---|---:|
| 回答通过率 | 1.0 |
| 生成拒答准确率 | 1.0 |
| 引用有效率 | 1.0 |
| 引用支持率 | 1.0 |

Rerank 相比无 Rerank 的提升：

| 指标 | 无 Rerank | Rerank | 变化 |
|---|---:|---:|---:|
| 回答通过率 | 0.8571 | 1.0 | +0.1429 |
| 生成拒答准确率 | 0.75 | 1.0 | +0.25 |
| 引用有效率 | 0.9286 | 1.0 | +0.0714 |
| 引用支持率 | 0.8571 | 1.0 | +0.1429 |

更完整的实验说明见：

[RAG + Rerank 实验报告](docs/rag_rerank_experiment_report.md)

## 主要接口

当前核心接口包括：

- `/documents/index`：上传 PDF 并入库
- `/documents`：列出已入库文档
- `/documents/{document_id}`：删除指定文档
- `/search/demo`：向量检索演示
- `/rag/chat`：无 Rerank RAG 问答
- `/rag/chat/rerank`：带 Rerank 的 RAG 问答

接口名称以当前代码为准，后续工程化分层时会继续整理。

## 评测脚本

项目内包含多类评测脚本：

```text
app/evaluation/evaluate_retrieval.py
app/evaluation/evaluate_generation.py
app/evaluation/evaluate_rerank.py
app/evaluation/evaluate_generation_rerank.py
app/evaluation/evaluate_rerank_params.py
app/evaluation/compare_retrieval_configs.py
```

用途：

- `evaluate_retrieval.py`：无 Rerank 检索评测
- `evaluate_generation.py`：无 Rerank 生成评测
- `evaluate_rerank.py`：Rerank 检索评测
- `evaluate_generation_rerank.py`：Rerank 生成评测
- `evaluate_rerank_params.py`：Rerank 参数组合对比
- `compare_retrieval_configs.py`：无 Rerank 检索参数网格对比

示例：

```powershell
python -m app.evaluation.evaluate_retrieval
python -m app.evaluation.evaluate_rerank --mode single
python -m app.evaluation.evaluate_rerank --mode comparison
python -m app.evaluation.evaluate_generation
python -m app.evaluation.evaluate_generation --top-k 5 --min-score 0.5
python -m app.evaluation.evaluate_generation_rerank --mode single
python -m app.evaluation.evaluate_generation_rerank --mode comparison
python -m app.evaluation.evaluate_rerank_params
```

## 自动化测试

项目已新增 pytest 自动化测试，用于把关键 Mock 回归固化下来，避免后续重构时破坏旧功能。

```text
tests/
├── test_config_defaults.py
├── test_evaluate_generation_metrics.py
├── test_evaluate_generation_rerank.py
├── test_evaluate_rerank.py
├── test_evaluation_cli.py
├── test_evaluation_cases.py
├── test_evaluation_comparisons.py
├── test_evaluation_utils.py
├── test_qdrant_service.py
├── test_rag_service.py
├── test_rerank_service.py
└── test_routes_and_errors.py
```

当前覆盖重点：

- 路由注册是否完整
- 统一异常响应是否正确
- RAG/Rerank 默认参数是否来自配置中心
- `search_chunks()` 是否返回 `vector_score`
- `rerank_chunks()` 是否根据 `index` 回填原 Source
- Rerank 失败时是否进入 `vector_fallback`
- 评测测试集是否保持 30 条、22 普通题、8 拒答题

运行方式：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

当前结果：

```text
67 passed
```

## 项目结构

当前项目已完成第一轮 Python 工程化分层。`app/main.py` 只负责创建 FastAPI app 并注册路由；接口、数据结构、业务逻辑和外部服务调用已经拆分到不同目录。

当前结构：

```text
rag-knowledge-base/
├── app/
│   ├── api/
│   │   ├── chat.py
│   │   ├── documents.py
│   │   ├── health.py
│   │   ├── rag.py
│   │   └── search.py
│   ├── core/
│   │   ├── __init__.py
│   │   ├── config.py
│   │   ├── exceptions.py
│   │   └── logging.py
│   ├── schemas/
│   │   ├── chat.py
│   │   ├── rag.py
│   │   └── search.py
│   ├── services/
│   │   ├── qwen_service.py
│   │   ├── rerank_service.py
│   │   ├── qdrant_service.py
│   │   ├── rag_service.py
│   │   ├── document_service.py
│   │   ├── chat_service.py
│   │   └── search_service.py
│   ├── main.py
│   ├── evaluation/
│   ├── evaluate_retrieval.py
│   ├── evaluate_generation.py
│   ├── evaluate_rerank.py
│   ├── evaluate_generation_rerank.py
│   └── evaluate_rerank_params.py
├── docs/
│   ├── rag_rerank_experiment_report.md
│   ├── main_refactor_plan.md
│   └── startup_guide.md
├── tests/
│   ├── test_config_defaults.py
│   ├── test_evaluation_cases.py
│   ├── test_qdrant_service.py
│   ├── test_rag_service.py
│   ├── test_rerank_service.py
│   └── test_routes_and_errors.py
├── pytest.ini
├── requirements.txt
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── .env.example
├── rag-learning-test-document.pdf
└── README.md
```

已完成的分层进度：

```text
schemas：已抽取 ChatRequest、SearchRequest、RagChatRequest、RerankRagChatRequest
qwen_service：已抽取 Embedding、批量 Embedding 和 Chat 调用
rerank_service：已抽取 qwen3-rerank 调用
qdrant_service：已抽取 Qdrant client、Collection 初始化、search_chunks、文档删除、文档列表和向量写入
rag_service：已抽取 Prompt 构建、无 Rerank RAG、Rerank RAG、拒答和 fallback 逻辑
document_service：已抽取 PDF 解析、文本切分、file_hash 计算和文档入库编排
chat_service/search_service：已抽取普通聊天、搜索演示和 Embedding 测试逻辑
api：已拆分 health、chat、documents、search、rag 路由，main.py 只负责注册 router
core/config：已集中管理 DASHSCOPE_API_KEY、DashScope Base URL、Qdrant 地址、Collection 名称、模型名称、超时时间和 RAG/Rerank 默认参数
core/logging：已统一配置日志输出，记录文档入库、Qdrant 检索、RAG/Rerank、fallback 和异常
core/exceptions：已统一业务异常和兜底异常处理，api 层不再重复编写 try/except
pytest：当前全量回归 67 passed，覆盖路由、统一异常、配置默认值、测试集结构、Qdrant source 字段、Rerank 映射、fallback、评测工具函数、CLI 参数解析和 JSON 保存
Docker Compose：已新增 Dockerfile、docker-compose.yml、.dockerignore 和 .env.example，支持一键启动 FastAPI + Qdrant
```

后续计划分层方向：

```text
app/
├── api/
├── core/
├── schemas/
├── services/
├── evaluation/
└── tests/
```

## 运行方式

完整启动说明见：

[启动与部署说明](docs/startup_guide.md)

本地 Python 启动：

```powershell
Copy-Item .env.example .env
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

Docker Compose 启动：

```powershell
Copy-Item .env.example .env
docker compose up --build
```

启动后访问：

```text
Swagger: http://127.0.0.1:8000/docs
健康检查: http://127.0.0.1:8000/health
Qdrant 检查: http://127.0.0.1:8000/qdrant/health
```

## 后续计划

- 30 条测试集的 Rerank 生成评测已完成，当前推荐配置为 `candidate_k=6`、`rerank_top_k=3`、`rerank_min_score=0.75`
- 评测脚本已迁移到独立 `app/evaluation/` 目录，并支持将评测结果保存为 JSON
- 已抽取基础 Chunk 支持率评测函数，并通过单元测试保护
- 将当前 FastAPI AI 服务接入后续 Spring Boot 企业知识库项目

## 最终验证清单

本地快速回归，不调用真实模型 API：

```powershell
.\.venv\Scripts\python.exe -m pytest
python -m py_compile app/main.py
```

服务启动后检查：

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
Invoke-RestMethod http://127.0.0.1:8000/health
Invoke-RestMethod http://127.0.0.1:8000/qdrant/health
```

需要 Qdrant、测试 PDF 已入库，并会调用真实通义千问 API 的评测：

```powershell
python -m app.evaluation.evaluate_retrieval
python -m app.evaluation.evaluate_rerank
python -m app.evaluation.evaluate_generation
python -m app.evaluation.evaluate_generation --top-k 5 --min-score 0.5
python -m app.evaluation.evaluate_generation_rerank --mode single
python -m app.evaluation.evaluate_generation_rerank --mode comparison
python -m app.evaluation.evaluate_rerank_params
```

## 当前最新进度

- 测试集已扩展到 30 条：22 条普通题、8 条拒答题。
- Rerank 阈值对比已完成：`0.72`、`0.75`、`0.78` 均保持 `answer_pass_rate=0.9545`，其中 `0.75` 和 `0.78` 的 `rejection_accuracy=1.0`。
- 当前推荐 `rerank_min_score=0.75`，因为它在保持拒答准确率的同时比 `0.78` 更宽松，后续更不容易误拒正常问题。
- 已补齐拒答短语，例如 `未提及`、`未说明`、`无法确定`、`无法判断`。
- 已把 `compare_rerank_min_scores()` 整理为返回结构化结果，并增加 `repeat_count` 和 `is_recommended` 字段。

## 后续任务流程

1. API 文档与示例整理：补充 `/rag/chat/rerank` 的请求、响应和推荐参数示例。
2. Spring Boot 接入准备：设计后端调用 FastAPI 的接口契约、错误码和返回结构。
3. 项目一总结整理：沉淀 RAG 流程、Rerank 价值、评测指标和调参结论，方便面试表达。

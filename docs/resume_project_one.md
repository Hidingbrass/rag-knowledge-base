# 项目一简历材料：FastAPI + Qdrant 本地知识库 RAG

## 简历项目名称

FastAPI + Qdrant + 通义千问企业知识库 RAG 问答系统

## 一句话描述

基于 FastAPI、Qdrant 和通义千问从零实现本地知识库 RAG 问答系统，支持 PDF 解析入库、多文档管理、指定文档问答、拒答、引用来源、qwen3-rerank 重排序、Hybrid 检索和自动化评测。

## 推荐简历写法

项目描述：

```text
基于 FastAPI + Qdrant + 通义千问实现企业知识库 RAG 问答系统，完成 PDF 解析、Chunk 切分、Embedding 入库、向量检索、Prompt 构建、问答生成、引用来源、拒答和 Rerank 优化闭环。项目重点不依赖 LangChain 黑盒封装，而是手动实现并评测 RAG 核心链路。
```

职责与亮点：

```text
- 设计并实现 PDF 文档入库流程：文件上传、逐页解析、文本切分、Chunk 元数据保存、批量 Embedding 和 Qdrant 向量写入。
- 实现多文档管理能力：document_id 隔离、文件 SHA-256 重复检测、文档列表统计、指定文档检索和删除。
- 实现无 Rerank 与 Rerank 两条 RAG 问答链路，保留 vector_score 并新增 rerank_score，通过 rerank_min_score 控制拒答。
- 接入 qwen3-rerank，对扩大召回候选集进行二次排序；Rerank 前不使用向量阈值过滤，避免提前丢失低向量分但高相关片段。
- 扩展 Hybrid 检索，将向量召回和关键词召回合并去重，并支持 vector/hybrid 两种候选检索模式对比。
- 建立 30 条评测集，覆盖 22 条普通问答和 8 条拒答题，评估 Hit@K、拒答准确率、答案关键词召回、引用有效率、引用支持率和 fallback 情况。
- 工程化拆分 FastAPI 项目结构，将 api、schemas、services、core、evaluation 分层，并统一配置、日志和异常处理。
- 使用 pytest 固化核心回归测试，覆盖路由注册、配置默认值、Qdrant source 字段、Rerank index 回填、fallback、评测工具函数和 CLI 参数解析。
- 提供 Dockerfile、docker-compose.yml 和启动文档，支持 FastAPI + Qdrant 一键启动。
```

## 推荐技术栈写法

```text
Python, FastAPI, Pydantic, Qdrant, DashScope, 通义千问 Embedding/Chat, qwen3-rerank, pytest, Docker, Docker Compose
```

## 可量化结果

当前测试集：

```text
30 条评测用例：22 条普通问答，8 条拒答题。
```

当前自动化测试：

```text
pytest 全量回归：72 passed。
```

历史 Rerank 优化效果：

| 指标 | 无 Rerank | Rerank | 变化 |
|---|---:|---:|---:|
| 回答通过率 | 0.8571 | 1.0 | +0.1429 |
| 生成拒答准确率 | 0.75 | 1.0 | +0.25 |
| 引用有效率 | 0.9286 | 1.0 | +0.0714 |
| 引用支持率 | 0.8571 | 1.0 | +0.1429 |

30 条测试集上的无 Rerank 检索基线：

| 指标 | 结果 |
|---|---:|
| Hit@3 | 0.8182 |
| 检索拒答准确率 | 0.625 |

Rerank 阈值实验结论：

```text
candidate_k=6、rerank_top_k=3 时，rerank_min_score=0.75 和 0.78 都能保持拒答准确率 1.0；
最终优先推荐 0.75，因为它在保证拒答质量的同时更宽松，未来更不容易误拒正常问题。
```

## 面试时可以强调的项目价值

这个项目不是只会调用大模型 API，而是把 RAG 拆成可解释、可评测、可优化的工程链路：

```text
文档解析 -> 切分 -> 向量化 -> 向量库 -> 检索 -> Rerank -> Prompt -> 生成 -> 引用 -> 评测
```

能体现的能力：

- AI 应用开发：Embedding、向量检索、Rerank、Prompt、拒答。
- 后端工程能力：接口分层、配置、日志、异常、测试、Docker。
- 质量意识：不是只看“能回答”，而是建立评测集和指标闭环。
- 面向企业场景：多文档、权限隔离的基础能力、引用可追溯、拒答防幻觉。

## 简历风险提示

不要写成“训练大模型”或“微调大模型”，这个项目的定位是大模型应用开发和 RAG 工程化。

更准确的表述：

```text
接入通义千问 Embedding、Chat 和 qwen3-rerank 能力，构建企业知识库问答链路。
```

不要写“完全解决幻觉”，更准确是：

```text
通过检索增强、引用来源、拒答机制和评测脚本降低幻觉风险。
```

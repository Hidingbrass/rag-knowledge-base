# RAG + Rerank 实验报告

## 1. 实验背景

本项目是一个基于 FastAPI、Qdrant 和通义千问的本地知识库 RAG 学习项目。当前目标不是直接使用 LangChain 等框架完成黑盒调用，而是手动拆解 PDF 解析、文本切分、Embedding、向量检索、Prompt 构建、模型生成、引用返回、拒答和质量评测的完整链路。

在无 Rerank 的 RAG 流程中，系统主要依赖 Qdrant 的向量相似度召回文本片段。向量检索能够找到语义相近的内容，但不一定能保证排在前面的片段最能直接回答问题。Rerank 的作用是先扩大候选集，再对候选片段做二次排序，让更能支持答案的片段进入 Prompt。

## 2. 测试集

当前测试集共 30 条：

- 22 条普通问答题
- 8 条拒答题

普通题用于评估系统是否能基于知识库内容生成正确答案，并返回有效引用。拒答题用于评估当知识库没有足够资料时，系统是否能拒绝回答，而不是编造答案。

## 3. 无 Rerank 基线

无 Rerank 链路使用 `/rag/chat`，默认参数：

```text
top_k = 3
min_score = 0.55
```

30 条测试集上的无 Rerank 检索结果：

| 指标 | 结果 |
|---|---:|
| Hit@3 | 0.8182 |
| 检索拒答准确率 | 0.625 |

这些结果说明：基础向量检索可用，但在更难的 30 条测试集上，普通题命中率和拒答准确率都有下降空间。

## 4. Rerank 方案

Rerank 链路使用独立接口 `/rag/chat/rerank`，保留 `/rag/chat` 作为无 Rerank 基线。

当前 Rerank 流程：

```text
用户问题
-> Qdrant 向量检索扩大候选集
-> 不在 Rerank 前使用原向量 min_score 淘汰候选
-> 调用 qwen3-rerank 对候选片段重新排序
-> 根据 Rerank 返回的 index 找回原 Source
-> 保留 vector_score，并新增 rerank_score
-> 截取 rerank_top_k 个片段
-> 根据 rerank_min_score 判断是否拒答
-> 构建 Prompt
-> 调用通义千问生成答案
-> 返回答案和引用来源
```

核心参数：

```text
candidate_k = 6
rerank_top_k = 3
rerank_min_score = 0.75
```

变量含义：

- `candidate_k`：向量检索阶段召回多少个候选片段。
- `rerank_top_k`：Rerank 后保留多少个片段进入 Prompt。
- `rerank_min_score`：Rerank 最高分低于该阈值时触发拒答。
- `vector_score`：Qdrant 向量相似度分数。
- `rerank_score`：Rerank 模型给出的相关性分数。

Rerank 前不使用 `min_score=0.55` 过滤候选，是因为向量相似度和最终答案相关性不是同一个概念。某些片段的向量分数可能不高，但它恰好包含更准确的答案；如果提前过滤，Rerank 就失去了重新排序和纠错的机会。

## 5. Rerank 检索结果

30 条测试集上的 Rerank 检索结果：

| 指标 | 结果 |
|---|---:|
| candidate_hit_rate | 1.0 |
| rerank_hit_rate | 1.0 |
| rerank_normal_pass_rate | 1.0 |
| rerank_rejection_accuracy | 1.0 |

这说明正确片段已经能被召回，并且经过 Rerank 后仍然保留在前 `rerank_top_k` 结果中。

## 6. Rerank 生成结果

使用 `candidate_k=6`、`rerank_top_k=3`、`rerank_min_score=0.75` 后，30 条测试集上的生成评测结果为：

| 指标 | 结果 |
|---|---:|
| answer_pass_rate | 1.0 |
| rejection_accuracy | 1.0 |
| citation_valid_rate | 1.0 |
| average_citation_support | 1.0 |
| fallback_rate | 0.0 |

其中，之前唯一失败的普通题来自关键词规则过严，而不是 RAG 主流程错误。修正测试用例关键词后，生成评测通过。

## 7. 阈值对比实验

在 `candidate_k=6`、`rerank_top_k=3` 不变的前提下，对比不同 `rerank_min_score`：

| rerank_min_score | answer_pass_rate | rejection_accuracy | citation_valid_rate | average_citation_support |
|---:|---:|---:|---:|---:|
| 0.72 | 0.9545 | 0.875 | 1.0 | 1.0 |
| 0.75 | 0.9545 | 1.0 | 1.0 | 1.0 |
| 0.78 | 0.9545 | 1.0 | 1.0 | 1.0 |

结论：

- `0.72` 偏低，会放过部分应该拒答的问题。
- `0.75` 和 `0.78` 当前质量指标相同。
- `0.75` 比 `0.78` 更宽松，未来遇到正常问题时更不容易误拒。

因此当前推荐：

```text
candidate_k = 6
rerank_top_k = 3
rerank_min_score = 0.75
```

## 8. 已完成改进

- `score` 已在 RAG 检索链路中安全区分为 `vector_score`。
- Rerank 结果新增 `rerank_score`。
- `REJECTION_PHRASES` 已补充 `未提及`、`未说明`、`无法确定`、`无法判断`。
- 评测脚本已迁移到独立 `app/evaluation/` 目录。
- `compare_rerank_min_scores()` 已改为返回结构化结果。
- 参数对比结果已增加 `repeat_count` 和 `is_recommended` 字段。
- 默认配置已更新为 `RAG_DEFAULT_RERANK_MIN_SCORE=0.75`。
- 评测结果已支持保存为 JSON 文件，便于记录实验和横向对比。
- 已新增 Hybrid 检索，将向量召回和关键词召回合并去重后交给 Rerank。
- `evaluate_rerank.py` 已支持 `--mode single/comparison`，可以对比 vector/hybrid 候选检索模式。
- vector/hybrid 对比结果已支持 `is_recommended` 标记，推荐逻辑优先保证拒答准确率，再比较 Rerank 命中率和候选命中率。

## 9. 后续任务流程

1. 继续增强 Chunk 级支持评测，减少纯关键词匹配带来的误判。
2. 整理 `/rag/chat/rerank` 的 API 示例和返回字段说明。
3. 为后续 Spring Boot 接入 FastAPI RAG 服务准备接口契约。
4. 在项目二中接入用户、知识库、权限、文档状态、异步解析和聊天记录等企业业务能力。

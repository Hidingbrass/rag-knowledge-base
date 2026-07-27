# RAG + Rerank 实验报告

## 1. 实验背景

本项目是一个基于 FastAPI、Qdrant 和通义千问的本地知识库 RAG 学习项目。当前目标不是直接使用 LangChain 等框架完成黑盒调用，而是手动拆解 PDF 解析、文本切分、Embedding、向量检索、Prompt 构建、模型生成、引用返回、拒答和质量评测的完整链路。

在无 Rerank 的 RAG 流程中，系统主要依赖 Qdrant 的向量相似度召回文本片段。向量检索能够找到语义相近的内容，但可能漏掉缩写、型号和专有名词等词法强相关片段。当前实现已经增加 Sparse Lexical Retrieval，并由 Qdrant 使用 RRF 融合 Dense/Sparse 候选，再用 Rerank 完成精排。

## 2. 测试集

历史单文稿回归集共 30 条：

- 22 条普通问答题
- 8 条拒答题

普通题用于评估系统是否能基于知识库内容生成正确答案，并返回有效引用。拒答题用于评估当知识库没有足够资料时，系统是否能拒绝回答，而不是编造答案。

2026-07-15 新增企业技术文档独立评测集：

- 6 份 PDF，共 12 页，覆盖系统架构、Hybrid RAG、安全、部署排障、API 和质量门禁。
- 26 条问题，其中 20 条可回答题、6 条无答案题。
- gold source 从固定页码升级为 PDF 文件名，支持跨文档 gold document。
- 题型覆盖语义改写、精确配置名、故障恢复、错误语义、跨文档和拒答。
- 评测指标增加 MRR、mean gold document recall 和 full gold hit at K。

该数据集来自当前仓库真实实现，PDF 已完成文本提取和逐页渲染检查。2026-07-16 已使用真实
DashScope Embedding/Rerank 和 Qdrant 完成六份 PDF 入库与四路在线消融。

## 3. 无 Rerank 基线

无 Rerank 链路使用 `/rag/chat`，默认参数：

```text
top_k = 3
min_score = 0.55
```

无 Rerank 检索历史基线结果：

| 指标 | 结果 |
|---|---:|
| Hit@3 | 0.8571 |
| 检索拒答准确率 | 0.5 |

这些结果说明：基础向量检索可用，但普通题命中率和拒答准确率都有优化空间。当前测试集已扩展到 30 条，后续重新入库测试文档后应再跑一次完整评测确认最新基线。

## 4. Rerank 方案

Rerank 链路使用独立接口 `/rag/chat/rerank`，保留 `/rag/chat` 作为无 Rerank 基线。

当前 Hybrid + Rerank 流程：

```text
文档入库
-> 通义千问生成 dense 向量
-> 中英文词法编码生成 sparse 向量
-> Qdrant 保存 dense / sparse 命名向量

用户问题
-> Dense 语义召回 + Sparse 词法召回
-> Qdrant Reciprocal Rank Fusion（RRF）
-> 不在 Rerank 前使用原向量 min_score 淘汰候选
-> 调用 qwen3-rerank 对候选片段重新排序
-> 根据 Rerank 返回的 index 找回原 Source
-> 保留 fusion_score，并新增 rerank_score
-> 使用候选排名与 Rerank 排名执行文档级 RRF
-> 按文档级 RRF 分数输出每个文档的最高 Rerank Chunk，再按精排顺序补满 rerank_top_k
-> 根据 rerank_min_score 判断是否拒答
-> 构建 Prompt
-> 调用通义千问生成答案
-> 返回答案和引用来源
```

核心参数：

```text
candidate_k = 6
sparse_limit = 6
rerank_top_k = 3
rerank_document_diversity = true
rerank_min_score = 0.75
```

变量含义：

- `candidate_k`：Dense 检索阶段召回多少个候选片段。
- `sparse_limit`：Sparse 倒排检索阶段召回多少个候选片段。
- `rerank_top_k`：Rerank 后保留多少个片段进入 Prompt。
- `rerank_document_diversity`：用候选排名与精排排名共同选择文档，再补充同文档 Chunk；单文档问答仍会补满 Top K。
- `rerank_min_score`：Rerank 最高分低于该阈值时触发拒答。
- `vector_score`：Qdrant 向量相似度分数。
- `sparse_score`：Sparse 单路查询的词法检索分数。
- `fusion_score`：Qdrant RRF 融合后的排序分数。
- `rerank_score`：Rerank 模型给出的相关性分数。

Rerank 前不使用 `min_score=0.55` 过滤候选，是因为向量相似度和最终答案相关性不是同一个概念。某些片段的向量分数可能不高，但它恰好包含更准确的答案；如果提前过滤，Rerank 就失去了重新排序和纠错的机会。

## 5. Rerank 检索结果

下表是旧版 Dense + Rerank 在 30 条测试集上的历史结果：

| 指标 | 结果 |
|---|---:|
| candidate_hit_rate | 1.0 |
| rerank_hit_rate | 1.0 |
| rerank_normal_pass_rate | 1.0 |
| rerank_rejection_accuracy | 1.0 |

这说明旧版正确片段已经能被召回，并且经过 Rerank 后仍然保留在前 `rerank_top_k` 结果中。Dense + Sparse + RRF 已于 2026-07-15 完成代码升级，下面单独记录新架构在企业技术文档集上的真实结果，避免与旧版单文稿历史基线混用。

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
- Hybrid 检索已从“扫描最多 1000 个 Chunk 后做字符串匹配”升级为 Qdrant Dense/Sparse 命名向量和服务端 RRF。
- Sparse 分支支持英文技术词、中文字符 bi-gram/tri-gram、稳定哈希维度、BM25 风格 TF 饱和和 Qdrant IDF。
- Source 分数已拆分为 `vector_score`、`sparse_score`、`fusion_score` 和 `rerank_score`。
- Rerank 失败时不再从 RRF 候选中静默丢弃无 `vector_score` 的结果，而是重新执行 Dense fallback。
- 新增 `evaluate_retrieval_ablation.py`，用于对比 Vector、Sparse、Hybrid RRF、Hybrid RRF + Rerank。
- 新增 `technical_docs` 数据集参数，按 gold PDF 文件名计算 Hit@K、MRR、文档召回率和完整 gold 命中率。
- 新增可复现的六文档企业技术语料、PDF 生成脚本和带默认限流节奏的批量上传脚本。
- `evaluate_rerank.py` 已支持 `--mode single/comparison`，可以对比 vector/hybrid 候选检索模式。
- vector/hybrid 对比结果已支持 `is_recommended` 标记，推荐逻辑优先保证拒答准确率，再比较 Rerank 命中率和候选命中率。

## 9. 企业技术文档真实四路消融

执行时间：2026-07-16 13:15:56。运行环境为本地 Docker Compose、真实 Qdrant 和真实
DashScope API。六份 PDF 全部入库成功，共产生 40 个 Chunk；评测参数为
`candidate_k=6`、`sparse_limit=6`、`rerank_top_k=3`。

| 模式 | Hit@K | MRR@K | mean gold document recall | full gold hit@K | P50 | P95 |
|---|---:|---:|---:|---:|---:|---:|
| Vector（K=6） | 1.0000 | 0.9500 | 1.0000 | 1.0000 | 1324.26 ms | 11241.20 ms |
| Sparse（K=6） | 1.0000 | 0.8792 | 1.0000 | 1.0000 | 2.80 ms | 4.28 ms |
| Hybrid RRF（K=6） | 1.0000 | 0.9500 | 1.0000 | 1.0000 | 1672.91 ms | 2961.16 ms |
| Hybrid RRF + Rerank（K=3） | 1.0000 | 0.9500 | 0.9750 | 0.9500 | 4048.69 ms | 7962.42 ms |

原始逐题结果：
[retrieval_ablation_20260716_131556.json](../app/evaluation/evaluation_results/retrieval_ablation_20260716_131556.json)。

结果解读：

- 四种模式在 20 条可回答题上均实现 `Hit@6=1.0`，证明新 Collection 和四路真实调用链可以运行；
  语料只有 6 份文档，因此这个数字不能外推为大规模生产效果。
- Sparse 不依赖远程 Embedding，延迟最低，但 MRR 为 `0.8792`，精确配置名和权限类问题的首位排序仍弱于 Vector/Hybrid。
- Hybrid 与 Vector 的 MRR 都是 `0.95`，当前数据不足以证明 RRF 提升了排序质量；Hybrid 的 P95
  低于本轮 Vector，但 Vector 受个别远程 API 长尾影响，单次实验不能得出 Hybrid 天然更快的结论。
- Rerank 把 `RAG-FALLBACK-008` 从第 2 位提升到第 1 位，但也把 `SEC-PERM-012` 从第 1 位降到第 2 位。
- `EVAL-MULTI-020` 需要同时命中 EVAL 与 SEC 两份文档。Rerank Top 3 中同一 EVAL 文档保留了两个
  Chunk，挤掉 SEC 文档，导致该题 gold document recall 为 `0.5`。这暴露的是文档级多样性问题，
  不是候选召回失败。
- 6 条无答案题本轮只进入延迟统计，尚未纳入检索拒答得分；拒答 F1、生成质量、Token 和成本需要下一轮生成评测补齐。

## 10. 文档级多样性优化与复测

针对 `EVAL-MULTI-020`，只按 Rerank 排名做文档去重仍然不足：SEC 在 Hybrid 候选中排第 2，
却被 Rerank 降到第 5。最终方案为跨阶段文档级 RRF：

```text
document_score = 1 / (60 + rerank_rank) + 1 / (60 + retrieval_rank)
```

- 开启多样性时请求完整 Rerank 候选排序。
- 每个文档取候选排名与 Rerank 排名融合后的最高分。
- 按文档融合分选出 Top K 文档，每个文档返回最高 Rerank Chunk。
- 文档数量不足时，再按原始 Rerank 顺序补满 Top K，保证单文档问答上下文数量。
- 拒答阈值检查所有入选 Chunk 的最高 `rerank_score`，不依赖最终文档排序的第一项。
- 可以用 `RAG_RERANK_DOCUMENT_DIVERSITY_ENABLED=false` 或 CLI
  `--no-rerank-document-diversity` 回到原始 Top K，便于 A/B。

同一批真实候选的目标 A/B：

| 策略 | Top 3 文档 | EVAL-MULTI-020 gold recall |
|---|---|---:|
| 原始 Rerank | OPS、EVAL、EVAL | 0.5 |
| 跨阶段文档级 RRF | EVAL、OPS、SEC | 1.0 |

2026-07-16 13:43:48 最终四路复测：

| 模式 | Hit@K | MRR@K | mean gold document recall | full gold hit@K | P50 | P95 |
|---|---:|---:|---:|---:|---:|---:|
| Vector（K=6） | 1.0000 | 0.9500 | 1.0000 | 1.0000 | 700.24 ms | 1232.16 ms |
| Sparse（K=6） | 1.0000 | 0.8792 | 1.0000 | 1.0000 | 1.68 ms | 3.07 ms |
| Hybrid RRF（K=6） | 1.0000 | 0.9250 | 1.0000 | 1.0000 | 697.52 ms | 1174.76 ms |
| Hybrid RRF + Rerank（K=3） | 1.0000 | 0.9750 | 1.0000 | 1.0000 | 2685.48 ms | 5646.89 ms |

最终逐题结果：
[retrieval_ablation_20260716_134348.json](../app/evaluation/evaluation_results/retrieval_ablation_20260716_134348.json)。

与初始 Rerank 实验相比，跨文档 gold 指标已从 `0.975/0.95` 修复为 `1.0/1.0`，MRR 从
`0.95` 提升到 `0.975`。不同轮次的 Hybrid MRR 和远程调用延迟存在波动，因此这里只把同批候选 A/B
作为因果证据，不把跨轮延迟差异归因于本次算法修改。

## 11. 后续任务流程

1. 对相同数据重复运行多轮并预热 Embedding，区分模型网络长尾和检索计算延迟。
2. 运行生成与拒答评测，补充拒答 Precision/Recall/F1、引用正确率、Token 和估算成本。
3. 根据逐题失败类型扩充 hard cases，并扩大文档数量后再调整 Dense/Sparse 候选数。

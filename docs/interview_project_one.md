# 项目一面试讲解手册

## 1. 30 秒项目介绍

这是一个基于 FastAPI、Qdrant 和通义千问的本地知识库 RAG 项目。用户上传 PDF 后，系统会解析文本、切分 Chunk、调用 Embedding 模型向量化，并写入 Qdrant。问答时，系统先检索相关片段，再构建 Prompt 调用通义千问生成答案，并返回引用来源。

后续我又接入了 qwen3-rerank 和 Hybrid 检索，并且做了评测脚本，能够对比无 Rerank、Rerank、不同阈值和不同检索模式的效果。这个项目重点是理解 RAG 底层链路和工程化，而不是直接用 LangChain 黑盒封装。

## 2. 完整链路怎么讲

### 文档入库链路

```text
上传 PDF
-> 校验文件类型
-> 读取文件 bytes
-> 计算 SHA-256，判断是否重复上传
-> 逐页解析 PDF 文本
-> 按 chunk_size 和 chunk_overlap 切分文本
-> 为每个 Chunk 保存 document_id、filename、page_number、chunk_index、text
-> 批量调用通义千问 Embedding
-> 写入 Qdrant Collection
```

面试表达：

```text
我没有只保存文本内容，而是把 document_id、文件名、页码和 chunk_index 都放进 Qdrant payload。这样后续问答可以返回引用来源，也可以按 document_id 做指定文档检索和删除。
```

### 无 Rerank 问答链路

```text
用户问题
-> 问题 Embedding
-> Qdrant 向量检索 top_k
-> 使用 min_score 过滤低相似度片段
-> 没有可用片段则拒答
-> 将片段拼接为带编号的 Prompt
-> 调用通义千问 Chat
-> 返回答案和 sources
```

面试表达：

```text
无 Rerank 链路适合作为基线。它简单、成本低，但只依赖向量相似度，容易把语义接近但不能直接回答问题的片段排到前面。
```

### Rerank 问答链路

```text
用户问题
-> 扩大候选召回 candidate_k
-> 不提前用 vector min_score 淘汰候选
-> 调用 qwen3-rerank
-> 根据返回的 index 找回原 Source
-> 保留 vector_score，新增 rerank_score
-> 截取 rerank_top_k
-> top rerank_score 低于 rerank_min_score 时拒答
-> 构建 Prompt 并生成答案
```

面试表达：

```text
Rerank 的核心价值是二次排序。向量检索负责粗召回，Rerank 负责精排。为了不给 Rerank 提前丢候选，我在 Rerank 前不使用原来的 vector min_score 过滤，而是把拒答判断放到 Rerank 之后，用 rerank_min_score 判断。
```

### Hybrid 检索链路

```text
问题
-> 向量检索召回 candidate_k
-> 关键词检索召回 keyword_limit
-> 根据 document_id + chunk_index 等 source key 合并去重
-> 保留 vector_score / keyword_score
-> 交给 Rerank 做统一排序
```

面试表达：

```text
Hybrid 检索解决的是单纯向量检索可能漏掉关键词强相关片段的问题。向量检索擅长语义相似，关键词检索擅长精确词命中，两者合并后再交给 Rerank 排序。
```

## 3. 为什么这样设计

### 为什么要有拒答

RAG 不是只要回答出来就行。知识库没有资料时，如果模型强行回答，就会产生幻觉。

本项目的拒答有两层：

- 无 Rerank：没有 source 达到 `min_score` 就拒答。
- Rerank：最高 `rerank_score` 低于 `rerank_min_score` 就拒答。

面试表达：

```text
我把拒答看成 RAG 系统的核心能力之一，因为企业知识库更怕编造答案。宁可明确说资料不足，也不能假装知道。
```

### 为什么要返回引用来源

引用来源让答案可追溯，用户可以知道答案来自哪个文件、哪一页、哪个 Chunk。

面试表达：

```text
引用来源一方面提升可信度，另一方面也方便做评测。比如我可以检查模型引用的编号是否合法，也可以检查被引用片段是否真的包含 expected_keywords。
```

### 为什么要区分 vector_score 和 rerank_score

`vector_score` 来自 Qdrant，表示向量相似度。

`rerank_score` 来自 qwen3-rerank，表示 Rerank 模型判断的问题和片段相关性。

面试表达：

```text
这两个分数来源不同、含义不同，不能混在一个 score 字段里。区分后可以分析到底是召回阶段的问题，还是重排序阶段的问题。
```

### 为什么 Rerank 前不使用 min_score

如果在 Rerank 前用向量分数过滤，可能会把向量分数不高但答案更准确的片段提前删掉。

面试表达：

```text
向量相似度和答案相关性不是完全一样的指标。Rerank 的作用就是纠正粗召回排序，所以我让向量检索多召回一点，然后把最终筛选交给 Rerank。
```

### 为什么保留无 Rerank 链路

无 Rerank 是基线，也是 fallback 的参考。

面试表达：

```text
保留无 Rerank 链路可以做 A/B 对比，也方便定位优化是否真的有效。如果直接把原链路替换掉，后面指标变好或变差都不好解释。
```

## 4. 评测体系怎么讲

本项目不是只靠手工问几个问题，而是建立了评测脚本。

当前测试集：

```text
30 条用例：22 条普通问答，8 条拒答题。
```

检索评测：

- `candidate_hit_rate`：扩大候选集里是否包含正确页。
- `rerank_hit_rate`：Rerank 后保留的 top_k 里是否包含正确页。
- `rejection_accuracy`：拒答题是否没有召回误导性片段，或是否正确拒答。

生成评测：

- `answer_pass_rate`：普通题关键词是否覆盖。
- `rejection_accuracy`：拒答题是否正确拒答。
- `citation_valid_rate`：引用编号是否合法。
- `average_citation_support`：引用片段是否支持答案关键词。
- `fallback_rate`：Rerank 失败后 fallback 的比例。

面试表达：

```text
我把 RAG 评测拆成检索评测和生成评测。检索评测看正确片段有没有被召回，生成评测看答案是否正确、引用是否合法、拒答是否稳定。这样可以定位问题发生在召回、重排、Prompt 还是生成阶段。
```

## 5. 当前结果怎么讲

自动化测试：

```text
pytest 全量回归：72 passed。
```

无 Rerank 检索基线：

```text
Hit@3 = 0.8182
检索拒答准确率 = 0.625
```

历史 Rerank 生成结果：

```text
回答通过率：0.8571 -> 1.0
生成拒答准确率：0.75 -> 1.0
引用有效率：0.9286 -> 1.0
引用支持率：0.8571 -> 1.0
```

当前推荐参数：

```text
candidate_k = 6
rerank_top_k = 3
rerank_min_score = 0.75
```

推荐理由：

```text
candidate_k=6 已能覆盖当前测试集需要的候选；rerank_top_k=3 能控制 Prompt 长度；rerank_min_score=0.75 在保证拒答准确率的同时比 0.78 更宽松，减少误拒正常问题的风险。
```

## 6. 高频追问与回答

### Q1：你这个项目和直接用 LangChain 有什么区别？

回答：

```text
LangChain 能快速搭建链路，但容易隐藏细节。我这个项目主要是学习和掌握 RAG 底层工程流程，所以手动实现了解析、切分、Embedding、Qdrant 入库、检索、Prompt、Rerank、拒答、引用和评测。这样我能解释每个参数为什么存在，也能定位问题发生在哪个阶段。
```

### Q2：RAG 为什么还会幻觉？

回答：

```text
RAG 只是给模型提供外部资料，但不能保证检索一定命中、资料一定完整、模型一定严格引用资料。如果召回错了、Prompt 不清楚，或者模型擅自补充，仍然会幻觉。所以我做了拒答、引用来源和评测脚本来降低风险。
```

### Q3：Rerank 提升在哪里？

回答：

```text
向量检索是粗召回，可能把语义接近但不能回答问题的片段排前面。Rerank 会基于问题和候选片段重新判断相关性，让更能支持答案的片段进入 Prompt。在我的历史评测中，Rerank 后回答通过率、拒答准确率、引用有效率和引用支持率都有提升。
```

### Q4：如果 Rerank 接口失败怎么办？

回答：

```text
我做了 fallback。Rerank 调用异常时，系统进入 vector_fallback，使用候选 source 中有 vector_score 且达到 fallback_min_score 的片段继续回答。如果 fallback 也没有可用片段，就拒答。这样不会因为外部模型接口失败导致整个问答不可用。
```

### Q5：怎么处理多文档？

回答：

```text
每次文档入库生成 document_id，并把 document_id、filename、page_number、chunk_index 写入 Qdrant payload。检索时可以传 document_id 做 Qdrant Payload Filter，删除文档时也按 document_id 删除全部向量点。
```

### Q6：怎么判断重复上传？

回答：

```text
上传后先读取文件 bytes，计算 SHA-256。如果 Qdrant 中已经存在相同 file_hash 的文档，就返回重复文档错误，避免重复入库和重复召回。
```

### Q7：你怎么选参数？

回答：

```text
我不是凭感觉选参数，而是写了评测脚本。先固定测试集，再对比 top_k、min_score、candidate_k、rerank_top_k、rerank_min_score 等参数。最终根据回答通过率、拒答准确率、引用有效率、引用支持率、fallback_rate 和耗时来选。
```

### Q8：Hybrid 检索有什么风险？

回答：

```text
Hybrid 会带来更多候选，也可能引入关键词噪声。所以我没有直接把关键词结果送进 Prompt，而是先和向量结果合并去重，再交给 Rerank 统一排序。评测脚本也支持 vector/hybrid 对比，并根据 rerank_hit_rate 和拒答准确率推荐模式。
```

### Q9：这个项目怎么接 Spring Boot？

回答：

```text
FastAPI 作为 AI 服务，负责文档解析、Embedding、向量库、检索、Rerank 和模型调用。Spring Boot 负责用户、权限、知识库、文档状态、聊天记录、MySQL 和 Redis。两边通过 HTTP 接口通信。这样 Java 负责业务系统，Python 负责 AI 能力，边界比较清晰。
```

### Q10：你觉得项目还有哪些不足？

回答：

```text
目前评测集规模还比较小，主要用于学习和回归验证；Chunk 支持率仍然带有关键词规则的简化判断；还没有接入真实多用户权限和异步任务队列。下一步会把它接到 Spring Boot 企业知识库项目里，补上用户权限、文档状态、异步解析、SSE 流式输出和调用日志。
```

## 7. 自我介绍中可以怎么带出来

```text
我最近主要做了一个 FastAPI + Qdrant 的 RAG 知识库项目。这个项目里我没有直接套框架，而是从 PDF 解析、Chunk 切分、Embedding 入库、向量检索、Rerank、Prompt 构建、引用来源和拒答评测一步步实现。后面我还把项目做了工程化拆分，补了配置、日志、异常、Docker 和 pytest 测试。通过这个项目，我对 RAG 的召回、重排、拒答和评测闭环有了比较完整的理解。
```

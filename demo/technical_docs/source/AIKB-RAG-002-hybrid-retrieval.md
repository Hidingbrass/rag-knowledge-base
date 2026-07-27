# AIKB Hybrid RAG 检索与生成链路

- 文档编号：AIKB-RAG-002
- 默认 Collection：rag_chunks_hybrid_v1
- 默认检索模式：hybrid

## 1. 双路向量入库

每个 PDF Chunk 同时写入名为 dense 和 sparse 的两类向量。dense 使用 text-embedding-v4 生成
1024 维向量，并在 Qdrant 中使用 Cosine 距离。sparse 是项目内置的轻量词法编码，用于补充精确术语、
配置名、错误码和中英文混合技术词的召回。

旧版单向量 Collection 与命名向量结构不兼容。系统默认切换到 rag_chunks_hybrid_v1；如果配置的
Collection 已存在但缺少 dense 或 sparse，启动入库时会提前报错，要求更换名称或重建后重新上传。

## 2. Sparse Lexical Encoder

稀疏编码会保留英文技术 Token，并对连续中文生成字符 bi-gram 和 tri-gram。Token 通过稳定的
BLAKE2b 哈希映射到稀疏维度，词频使用 BM25 风格的 TF 饱和，避免同一个词重复很多次就无限放大权重。
Qdrant Collection 对 sparse 向量启用 IDF Modifier，使罕见词比常见词更有区分度。

这不是完整 BM25。当前实现没有按文档长度做归一化，也没有维护传统搜索引擎的可解释词典。项目文档和
面试说明必须如实称为“BM25 风格 TF + Collection IDF 的稀疏词法检索”，不能宣称已经实现完整 BM25。

## 3. Qdrant RRF 融合

Hybrid 查询先并行预取 Dense 候选和 Sparse 候选，再由 Qdrant 服务端执行 Reciprocal Rank Fusion。
默认 candidate_k 为 6，sparse_limit 为 6，配置项分别由 Spring Boot 的 default-candidate-k 和
default-sparse-limit，以及 FastAPI 的 RAG_DEFAULT_CANDIDATE_K 和 RAG_DEFAULT_SPARSE_LIMIT 控制。

RRF 主要利用两个结果列表中的排名，而不是直接把 Cosine 分数和稀疏分数相加，因此能避免不同分数量纲
难以校准的问题。融合结果记录 fusion_score；它不是向量相似度，不能使用 min_score 当作 Cosine 阈值。

## 4. Rerank 与拒答

Hybrid 候选交给 qwen3-rerank 重新排序。默认保留 rerank_top_k=3；如果最高 rerank_score 低于
rerank_min_score=0.75，系统直接返回“知识库中没有足够相关的资料”，不调用生成模型编造答案。

如果 Rerank API 调用失败，系统把 retrieval_mode 标为 vector_fallback。对于 Hybrid 请求，它会重新
执行一次 Dense 检索，再使用 fallback_min_score 过滤。不能直接拿 RRF fusion_score 与向量阈值比较。
响应中的 candidate_retrieval_mode 用于说明候选阶段原本是 vector 还是 hybrid。

## 5. 分数和引用

Source 数据分别保留 vector_score、sparse_score、fusion_score、rerank_score，字段只在对应阶段有意义。
前端展示优先级是 Rerank、Fusion、Vector、Sparse。生成 Prompt 会携带文件名、页码、Chunk 编号和当前
阶段可用分数，并要求模型只使用允许的 [1]、[2] 等引用编号。

只有检索到片段并不等于回答正确。评测至少要分开观察候选召回、Rerank 排序、拒答、引用合法性和引用
内容支持度，不能只展示一个“看起来不错”的聊天截图。

## 6. 参数边界

candidate_k 允许 1 到 20，sparse_limit 允许 1 到 20，rerank_top_k 允许 1 到 10，并且
rerank_top_k 不能大于 candidate_k。sparse_limit 兼容旧请求字段 keyword_limit，但新客户端应发送
sparse_limit。改变这些参数后应重新运行消融评测，而不是凭经验直接宣称效果更好。

# 架构决策记录

这份文档记录项目中几个核心技术决策。它的作用是帮助自己和面试官快速理解：为什么这样设计，而不是只看到“用了哪些技术”。

## ADR-001：使用 Spring Boot + FastAPI 双后端

决策：

```text
Spring Boot 作为业务后端，FastAPI 作为 AI 服务。
```

原因：

```text
Spring Boot 更适合承载企业业务系统能力：Controller / Service / Repository 分层、MySQL 事务、权限校验、统一响应、异常处理和静态页面入口。

FastAPI 更适合承载 AI 能力：PDF 解析、文本切分、Embedding、Qdrant 检索、Rerank、Prompt 编排、模型 JSON 解析和评测脚本。
```

收益：

```text
业务边界和 AI 边界清晰。
前端只调用 Spring Boot，不直接暴露 FastAPI 和模型服务。
后续扩展其他 AI 场景时，可以复用同一套 Spring Boot + FastAPI 边界。
```

代价：

```text
多了一个服务间 HTTP 调用。
本地启动和排查需要同时关注 Java、Python、MySQL、Qdrant 和 DashScope。
```

## ADR-002：MySQL 保存业务数据，Qdrant 保存向量数据

决策：

```text
MySQL 保存知识库、文档状态、聊天会话、聊天消息、求职分析、生成历史、简历版本和岗位收藏。
Qdrant 保存文本 Chunk 向量和检索 payload。
```

原因：

```text
MySQL 适合保存关系型业务数据和权限归属。
Qdrant 适合做高维向量相似度检索。
业务权限、会话关系和任务历史不适合塞进向量库；高维向量检索也不适合直接放到普通 MySQL 表里做。
```

收益：

```text
权限校验、历史查询、删除和更新都由 Spring Boot + MySQL 管理。
RAG 检索由 FastAPI + Qdrant 管理。
业务数据和向量数据职责清晰，便于解释和排查。
```

代价：

```text
需要维护 MySQL 文档记录和 Qdrant document_id / payload 之间的一致性。
删除文档时要同时处理业务记录和向量数据。
```

## ADR-003：正式问答入口放在 Spring Boot 会话接口

决策：

```text
正式问答使用 /api/chat/sessions/{sessionId}/ask。
旧版 /api/rag/ask 作为不安全入口停用。
```

原因：

```text
RAG 问答必须先校验用户是否能访问当前会话、知识库和文档。
如果前端直接调用简单 RAG 接口并传 documentId，容易绕过业务权限。
```

收益：

```text
权限判断集中在 Spring Boot。
聊天消息可以统一落 MySQL。
documentId 是否属于当前知识库可以在调用 FastAPI 前完成校验。
```

代价：

```text
接口链路更长：前端 -> Spring Boot -> FastAPI -> Qdrant / DashScope。
调试时需要同时查看 Spring Boot 和 FastAPI 日志。
```

## ADR-004：使用 Rerank 和阈值拒答降低幻觉

决策：

```text
保留无 Rerank 基线，同时提供 Rerank RAG 链路。
向量检索扩大候选集，Rerank 后按 rerank_min_score 判断是否拒答。
```

原因：

```text
向量检索只表示语义相似，不一定表示片段能直接支持答案。
企业知识库更怕模型编造答案，因此相关性不足时应该拒答。
```

收益：

```text
Rerank 让更能支持答案的片段进入 Prompt。
拒答阈值让系统在资料不足时明确说明不能回答。
vector_score 和 rerank_score 分开保存，方便定位召回问题还是重排问题。
```

代价：

```text
Rerank 会增加一次模型调用成本和延迟。
阈值需要通过评测集调参，否则可能误拒或误答。
```

## ADR-005：求职辅助 Agent 复用同一套架构

决策：

```text
求职 Agent 继续使用 Spring Boot + FastAPI 边界。
FastAPI 负责 Prompt、模型调用和 JSON 解析；Spring Boot 负责业务接口、userId、MySQL 历史和前端展示。
```

原因：

```text
求职 Agent 本质也是 AI 应用工作流，和 RAG 共用模型调用、Prompt 编排和结构化解析能力。
但它的历史记录、收藏岗位、简历版本和结果对比属于业务数据，应该放在 Spring Boot + MySQL。
```

收益：

```text
证明架构不是只能做知识库问答，也能扩展到新 AI 场景。
生成历史、简历版本、收藏岗位和分析对比都可以持久化和复用。
```

代价：

```text
FastAPI 和 Spring Boot 都需要新增 DTO / schema。
模型输出 JSON 需要更严格的解析和测试覆盖。
```

## ADR-006：提交前检查和 GitHub Actions 使用同一套脚本

决策：

```text
本地运行 scripts/pre_submit_check.sh。
GitHub Actions CI 也运行同一个脚本。
```

原因：

```text
如果本地和 CI 使用两套命令，容易出现本地通过但 CI 失败，或者 CI 通过但本地漏检。
```

收益：

```text
质量入口统一。
每次提交前检查和 GitHub Actions 结果更容易对应。
Markdown 链接、密钥扫描、Docker Compose 配置、FastAPI 测试和 Spring Boot 测试都在同一个入口里。
```

代价：

```text
脚本需要兼容本地 macOS 和 GitHub Actions Ubuntu 环境。
例如 Python 命令要兼容 .venv/bin/python、python3 和 python。
```

## ADR-007：保留 debug.html

决策：

```text
保留原始 debug.html，同时提供 Vue3 企业工作台 index.html。
```

原因：

```text
Vue3 工作台适合演示完整产品体验。
debug.html 适合排查接口请求和响应，尤其在联调、权限错误、字段不匹配时更直接。
```

收益：

```text
演示和排查分开。
面试时可以展示工作台；开发时可以快速定位接口问题。
```

代价：

```text
需要确保静态页面入口都能被测试覆盖，不要出现某个入口长期失效。
```

## ADR-008：使用 Flyway 管理业务库表结构

决策：

```text
Spring Boot 业务库表结构由 Flyway SQL 迁移文件管理。
Hibernate ddl-auto 从自动建表/更新改为 validate，只负责启动时校验 Entity 和数据库结构是否一致。
```

原因：

```text
JPA 自动建表适合学习早期快速迭代，但真实项目需要知道每次表结构变更是什么、何时发生、如何在新环境复现。
Flyway 把数据库结构变成可审查、可提交、可回放的版本化代码。
```

收益：

```text
新环境启动时可以自动创建业务表。
测试环境和本地 MySQL 使用同一套迁移来源。
如果 Entity 和 SQL 不一致，Spring Boot 启动阶段会直接失败，避免静默产生错误表结构。
```

代价：

```text
以后新增字段或表时，不能只改 Entity，还要新增 V2、V3 这类迁移 SQL。
已有本地旧库首次接入 Flyway 时需要建立 baseline，避免重复执行初始化建表脚本。
```

## ADR-009：在 Spring Boot 侧记录 FastAPI AI 调用日志

决策：

```text
Spring Boot 在 FastApiRagClient 统一记录调用 FastAPI 的业务类型、接口、耗时、成功失败、错误摘要、
模型名、重试次数、Token 和估算费用。
日志保存到 MySQL ai_call_log 表，并提供最近调用和时间窗口聚合接口供工作台展示。
```

原因：

```text
Spring Boot 是业务入口，知道一次调用属于文档入库、RAG 问答还是求职 Agent。
把记录点放在 FastApiRagClient，可以覆盖所有 Spring Boot -> FastAPI 调用，避免每个 Service 重复写耗时代码。
```

收益：

```text
可以排查 AI 服务慢调用和失败调用。
面试演示时能说明系统具备基础可观测性，而不是只会调用模型。
FastAPI 返回请求级模型 usage 后，可直接形成 24 小时成功率、平均/P95 延迟、Token、费用和分业务统计。
```

代价：

```text
Token 和费用依赖上游 usage 与配置单价；没有 usage 的调用记为 0，估算费用不能替代供应商账单。
日志保存失败不能影响主链路，因此日志 Service 会吞掉自身写入异常并输出 warn 日志。
```

## ADR-010：使用 Dense + Sparse + RRF 作为 Hybrid 候选检索

决策：

```text
Qdrant Collection 使用 dense 和 sparse 两类命名向量。
Dense 分支使用通义千问 Embedding，Sparse 分支使用可解释的中英文词法特征。
Qdrant 对两路候选执行 Reciprocal Rank Fusion（RRF），融合结果再交给 qwen3-rerank 精排。
```

原因：

```text
Dense 检索擅长语义相似，但可能漏掉型号、缩写、专有名词等精确词项。
Sparse 检索可以补充词法匹配，但单独使用又容易漏掉同义表达。
RRF 只依赖两路排序名次，不要求 Dense 和 Sparse 分数处在同一量纲，适合异构召回融合。
```

实现约束：

```text
Sparse 编码包含英文技术词、中文字符 bi-gram/tri-gram、稳定哈希维度和 BM25 风格 TF 饱和。
Collection 级 IDF 由 Qdrant SparseVectorParams 的 IDF modifier 计算。
该实现没有计算完整 BM25 的文档长度归一化，因此对外称为 Sparse Lexical Retrieval，不夸大为完整 BM25。
```

收益：

```text
删除了原先 scroll 1000 个 Chunk 后在 Python 中做字符串包含判断的线性扫描。
候选召回由 Qdrant Dense HNSW、Sparse 倒排索引和服务端 RRF 完成。
Source 明确区分 vector_score、sparse_score、fusion_score 和 rerank_score。
新增消融脚本，可对比 Vector、Sparse、Hybrid RRF、Hybrid RRF + Rerank 的命中率与延迟。
```

代价：

```text
旧版单向量 Collection 与命名向量结构不兼容，需要重建 Collection 并重新上传文档。
Sparse 哈希存在理论碰撞概率，中文字符 n-gram 也可能带来额外噪声，需要通过评测集持续校准。
RRF 分数不能直接当成语义相似度阈值，因此最终拒答仍使用 Rerank 分数；Rerank 故障时重新执行 Dense fallback。
```

## ADR-011：使用版本化企业技术语料和 Gold Document 评测

决策：

```text
把当前项目的真实架构、接口、安全、运维和评测规则整理成六份独立技术文档。
Markdown 作为可审查事实源，PDF 作为真实入库资产，manifest 固定文档编号和文件名。
独立评测集使用 PDF 文件名作为 gold source，并允许一道题包含多个 gold document。
```

原因：

```text
单份两页文稿只能验证最短 RAG 链路，容易得到过于理想且缺乏代表性的页码命中结果。
企业技术检索经常包含配置名、错误码、语义改写、跨文档问题和无答案问题，需要多文档数据集。
稳定文件名比生成后的页码更适合作为跨文档检索真值。
```

收益：

```text
演示数据与当前代码事实一致，可由脚本重复生成和上传。
消融评测除 Hit@K 外增加 MRR、gold document recall 和完整 gold 命中率。
PDF 逐页渲染和关键词一致性测试可以提前发现排版错误、空文档和标注漂移。
```

代价：

```text
六份文档会增加 Embedding 与 Rerank 在线实验成本，批量上传还要遵守每分钟 AI 限流。
当前评测集仍是人工构建的小规模工程集，不能代表生产流量，后续需要真实匿名 hard cases 扩充。
```

## ADR-012：采用确定性输出校验和分层不可信输入防护

决策：

```text
RAG 输出先做拒答和引用编号的确定性校验，不再额外调用一个模型充当唯一裁判。
用户问题、检索片段、简历和 JD 都作为不可信数据包裹；只有“越权指令 + 密钥/系统提示提取”组合命中时阻断。
联系方式等非任务必要信息在模型调用前脱敏，MySQL 敏感文本可使用 AES-256-GCM 字段级加密。
```

原因：

```text
让模型验证模型会继续引入幻觉、延迟和费用，引用编号与字段范围更适合由程序确定性判断。
单关键词封禁会误伤“如何防范提示词注入”等正常学习问题，因此采用组合信号和数据边界提示。
业务数据需要同时覆盖传输给模型前的最小化和落库后的泄露风险。
```

边界：

```text
规则只能防住已建模的高置信攻击，不宣称完全解决提示词注入。
AES 密钥必须由部署环境通过 SENSITIVE_DATA_ENCRYPTION_KEY 提供并妥善备份；未配置时本地开发保持兼容明文。
```

## ADR-013：在模型适配层统一用量统计、受控重试和轻量熔断

决策：

```text
Embedding、Rerank 和 Chat 在同一请求上下文中记录模型、尝试次数、Token、耗时和估算费用。
仅对连接失败、超时、429 和 5xx 指数退避重试；连续失败达到阈值后按模型能力开启进程内熔断。
流式响应一旦开始输出，不自动重放，避免内容重复和重复计费。
```

原因与边界：

```text
瞬时故障值得短暂重试，参数错误和鉴权错误重试没有意义。
当前熔断状态仅在单个 FastAPI 进程内生效；多副本生产部署应改用集中指标和网关/服务网格治理。
估算成本来自可配置单价，模型价格变化后需要同步环境变量。
```

## ADR-014：在 Spring Boot 使用确定性小聊路由保护 RAG 边界

决策：

```text
在正式会话入口完成权限和文档归属校验后，由 Spring Boot 对纯问候、感谢和能力询问执行有限整句白名单匹配。
命中后使用确定性模板直接回复，不调用 FastAPI、Embedding、Qdrant、Rerank 或生成模型，也不消耗 AI 模型限流次数。
该阶段所有未命中消息继续进入现有严格 RAG 链路；后续由 ADR-015 在不放宽企业知识边界的前提下增加分类器兜底。
```

原因：

```text
“你好”“谢谢”“你能做什么”不是知识检索问题，把它们送入 RAG 只会产生无意义的远程调用和生硬拒答。
这些意图范围小且答案固定，程序规则比大模型分类延迟更低、成本更小、行为更可预测。
企业知识问题仍必须经过检索、阈值和引用约束，不能因为增加小聊能力而模糊可信边界。
```

实现约束：

```text
规则只做 NFKC、大小写、空白和标点规范化后的整句匹配，不使用 contains；“你好，请总结这份资料”和“你好像没有回答问题”必须继续走 RAG。
直答与 RAG 回答复用同一套会话权限、消息持久化和 NDJSON accepted/delta/done 协议。
直答消息使用 small_talk 检索模式且不返回引用来源，便于前端和审计区分是否发生知识检索。
后续演进见 ADR-015 的“规则优先 + 分类模型兜底” Query Router。
```

代价：

```text
规则只能覆盖明确建模的短消息，需要用误判用例持续回归。
新增小聊表达时必须保持高精度，宁可让未识别表达进入 RAG，也不能截断包含真实知识问题的消息。
```

## ADR-015：规则后置轻量分类器与保守 RAG 回退

决策：

```text
正式会话完成 JWT、知识库和文档归属校验后，按“确定性小聊规则 -> 企业知识保护规则 -> 轻量分类器”路由。
分类器只输出 KNOWLEDGE_QA、TOOL_CALL、CLARIFICATION、OPEN_DOMAIN_CHAT、置信度和企业知识标记，不生成答案。
明确企业知识、低于 0.80、未知标签、无效结构或分类服务异常全部进入严格 RAG；只有高置信非企业意图可以绕开检索。
```

原因：

```text
纯规则无法覆盖公开知识、计算、创作、外部操作和不完整请求，把它们全部送入 RAG 会产生不必要的拒答。
概率分类可能误判，因此不能让分类器拥有放宽企业可信边界的最终决定权；强规则、企业标记、阈值和失败回退共同形成保守门禁。
分类与非 RAG 回答使用独立模型调用，便于单独统计延迟、Token、费用和失败率。
```

实现约束：

```text
企业知识仍经过原有权限、指定文档检索、Rerank 阈值、引用校验和拒答，分类器不会在 RAG 拒答后再次尝试开放域回答。
非 RAG 回答不返回伪造引用，助手消息用 open_domain_chat、tool_call 或 clarification 作为 retrievalMode 保存。
当前没有通用工具执行器；TOOL_CALL 只能给出步骤或追问必要参数，不得声称外部操作已经执行。
同步与流式入口共用同一个路由决策；流式非 RAG 分支当前以单个 delta 返回完整答案，不伪装为 Token 级流式。
```

代价与后续验证：

```text
规则未命中的非小聊请求通常增加一次分类模型调用；开放域回答总计需要分类加生成两次模型调用。
分类准确率尚未用真实匿名流量评测，需要建立混淆矩阵，优先跟踪企业知识被误放到非 RAG 的严重错误。
工具执行需要后续增加显式工具注册、参数 Schema、权限、幂等和操作审计，不能仅靠 intent 标签直接执行。
```

## ADR-016：确定性风险门禁与分类型分类故障降级

决策：

```text
在轻量分类器之前增加 Spring Boot 确定性策略路由，顺序为写操作门禁 -> 企业知识保护 -> 实时查询门禁。
删除、修改、发送、发布等写操作不进入 RAG、不调用普通聊天模型，也不消耗 AI 模型限流；当前没有授权工具时直接阻断。
天气、汇率、股价等明确实时问题在没有可验证工具时透明说明不可用，不再因分类器超时进入 RAG 猜测实时结果。
其余分类故障仍保守进入 RAG，确保无法判定范围的问题不会绕开企业资料引用边界。
```

结构化契约：

```text
分类结果除 intent、confidence 和 enterprise_knowledge 外，增加 knowledge_scope、operation、freshness、tool_name、missing_fields 和 requires_confirmation。
WRITE_TOOL 或 requires_confirmation 优先于企业知识回答分支，由 Spring Boot 强制阻断；READ_TOOL 缺参时进入澄清，无缺参但工具不可用时透明降级。
Flyway V7 为 chat_message 增加 routing_decision_json，持久化最终路由、决策来源、原因、置信度和风险维度。
```

验证边界：

```text
已建立独立人工标注意图路由集和自动评测程序，但尚未运行真实 DashScope 全集，不声明分类准确率、混淆矩阵结果或 0.80 已经数据校准。
当前没有 ToolRegistry 或真实工具执行器；阻断写操作不等于已经实现授权后的写操作执行。
规则只覆盖高置信模式，剩余问题仍需要分类器和保守 RAG 回退共同处理。
```

# 完整项目高频面试问答

这份文档用于准备“Spring Boot + FastAPI 企业知识库 RAG 系统 + 求职辅助 Agent”的完整项目面试。重点不是背答案，而是理解每个设计背后的目标、输入输出、流程和取舍。

## 1. 30 秒项目介绍

可以这样说：

```text
我做了一个 Spring Boot + FastAPI 的企业智能知识库 RAG 系统，并在同一套架构上扩展了求职辅助 Agent。

Spring Boot 负责知识库、文档状态、权限校验、聊天会话、聊天消息、求职分析历史、生成历史、简历版本和岗位收藏等业务能力；FastAPI 负责 PDF 解析、文本切分、Embedding、Qdrant 检索、qwen3-rerank、Prompt 编排和通义千问调用。

MySQL 保存业务数据，Qdrant 保存文本 Chunk 向量。项目支持 PDF 入库、指定文档问答、引用来源、拒答、Rerank、自动化评测、Vue3 工作台、Docker Compose 一键启动和求职 Agent 的简历解析、JD 解析、简历优化、面试准备、STAR 答案、历史对比与导出。
```

## 2. 架构设计

### Q1：为什么要用 Spring Boot + FastAPI 双后端？

回答：

```text
因为业务系统和 AI 链路的关注点不同。

Spring Boot 更适合做企业业务后端，例如权限、状态流转、MySQL 事务、Controller、Service、Repository 分层和统一响应。

FastAPI 更适合做 AI 服务，例如 Prompt 编排、模型调用、JSON 解析、Embedding、Rerank、评测脚本和 Python 生态里的 PDF 处理。

这样拆分后，前端统一调用 Spring Boot，Spring Boot 再调用 FastAPI。业务权限不会散落到 AI 服务里，模型调用细节也不会污染 Java 业务层。
```

### Q2：为什么 MySQL 和 Qdrant 都要用？

回答：

```text
它们保存的数据类型不一样。

MySQL 保存业务关系数据，例如知识库、文档状态、用户归属、聊天会话、聊天消息、求职分析任务、简历版本和收藏岗位。

Qdrant 保存文本 Chunk 的向量和检索 payload，负责相似度搜索。

如果把业务权限和会话关系放进向量库，会让业务查询和权限控制变复杂；如果把向量塞进 MySQL，又不适合做高效相似度检索。所以两者分工更清晰。
```

### Q3：为什么前端不直接调用 FastAPI？

回答：

```text
前端参数不能被信任，权限和业务状态必须放在后端。

正式入口放在 Spring Boot，Spring Boot 会先校验用户是否能访问知识库、文档和会话，再调用 FastAPI 完成 AI 能力。

这样可以避免前端绕过业务层直接传 document_id 调 FastAPI，造成跨知识库或跨用户访问。
```

## 3. 文档入库

### Q4：PDF 上传后的完整流程是什么？

回答：

```text
前端上传 PDF 到 Spring Boot。
Spring Boot 创建或复用文档记录，计算文件 SHA-256，判断是否重复上传。
如果不是重复文档，Spring Boot 调用 FastAPI。
FastAPI 解析 PDF、按 chunk_size 和 chunk_overlap 切分文本、调用通义千问 Embedding、把向量和 payload 写入 Qdrant。
FastAPI 返回 fastApiDocumentId 和 chunkCount。
Spring Boot 把文档状态更新为 AVAILABLE；如果失败则更新为 FAILED 并保留失败原因。
```

### Q5：为什么要保存文档状态？

回答：

```text
文档入库不是瞬间完成的，它依赖 PDF 解析、模型接口和 Qdrant 写入，任何一步都可能失败。

保存 PENDING、AVAILABLE、FAILED 这类状态后，前端可以展示处理进度，用户也能知道文档是否可问答。失败原因落库后也方便排查，而不是只在日志里找。
```

### Q6：重复上传怎么处理？

回答：

```text
系统会读取 PDF bytes 计算 SHA-256。

同一知识库内，如果已经存在相同 fileHash 且文档状态可复用，就不重复调用 FastAPI 入库，避免重复解析、重复向量化和重复写入 Qdrant。

FAILED 记录不会作为可复用文档，因为失败文档没有可靠的向量数据。
```

## 4. RAG 问答

### Q7：RAG 问答链路怎么走？

回答：

```text
用户在前端选择会话和文档后发起提问。
Spring Boot 校验当前用户是否能访问会话和知识库，并校验 documentId 是否属于当前知识库。
校验通过后调用 FastAPI 的 Rerank RAG 接口。
FastAPI 对问题做 Embedding，到 Qdrant 召回候选 Chunk，再调用 qwen3-rerank 做二次排序。
如果最高 rerank_score 低于阈值，就拒答；否则把 top 片段拼入 Prompt，调用通义千问生成答案，并返回引用来源。
Spring Boot 保存用户消息和助手消息到 MySQL。
```

### Q8：如何减少幻觉？

回答：

```text
我做了四层控制。

第一，答案基于检索片段生成，不让模型只靠自身知识自由发挥。
第二，返回引用来源，用户可以追溯答案来自哪个文件、页码和 Chunk。
第三，Rerank 后使用 rerank_min_score 做拒答，资料不足时明确说不能回答。
第四，用评测集验证回答通过率、拒答准确率、引用有效率和引用支持率。
```

### Q9：为什么要区分 vector_score 和 rerank_score？

回答：

```text
vector_score 来自 Qdrant，表示向量相似度；rerank_score 来自 qwen3-rerank，表示问题和候选片段的相关性。

这两个分数来源不同、含义不同。如果混成一个 score，后续就没法判断问题出在召回阶段还是重排序阶段。
```

### Q10：为什么 Rerank 前不先用 min_score 过滤？

回答：

```text
向量相似度和答案相关性不是完全一样的指标。

有些片段向量分数不一定最高，但可能更能直接回答问题。如果在 Rerank 前就用 vector min_score 删掉它，Rerank 就没有机会纠正粗召回排序。

所以我先扩大候选召回，再把最终筛选交给 Rerank 和 rerank_min_score。
```

## 5. 权限和持久化

### Q11：权限是怎么做的？

回答：

```text
当前版本已接入 Spring Security + JWT，业务 API 默认要求登录，用户身份从 Token 获取。

知识库保留 owner 和 department 字段，但两者职责不同：owner 是授权边界，department 在消费级产品中只是学习方向元数据。只有 owner 可以访问自己的知识库；文档、会话和消息都通过知识库关系间接复用这条规则。

旧版 userId 和 department 匿名联调只在显式开启 legacy 演示模式时可用；生产环境身份来自 JWT。测试还覆盖了两个同学习方向账号的越权场景，证明 department 不会扩大访问范围。
```

### Q12：为什么聊天消息要落 MySQL？

回答：

```text
聊天系统不是只要当次回答就结束。

会话和消息落 MySQL 后，页面刷新、服务重启或者用户重新打开页面时都能恢复历史。对求职 Agent 也是一样，分析结果、生成历史、简历版本和岗位收藏都要持久化，才像一个完整产品。
```

### Q13：如何防止跨文档或跨知识库问答？

回答：

```text
用户提问时，Spring Boot 不会直接相信前端传来的 documentId。

它会先查当前会话所属知识库，再确认 documentId 属于这个知识库，并且当前用户有权限访问这个知识库。校验通过后才调用 FastAPI 检索指定文档。
```

## 6. 求职辅助 Agent

### Q14：求职 Agent 为什么放在这个项目里？

回答：

```text
它是同一套 AI 应用架构的扩展场景。

RAG 证明系统能做知识库问答；求职 Agent 证明这套 Spring Boot + FastAPI 边界也能承载其他 AI 工作流。

FastAPI 继续负责 Prompt、模型调用和结构化 JSON 解析；Spring Boot 继续负责业务接口、userId、MySQL 历史和前端展示。
```

### Q15：求职 Agent 现在有哪些能力？

回答：

```text
包括简历与岗位 JD 匹配分析、简历结构化解析、JD 结构化解析、简历优化建议、面试准备包、STAR 面试答案、生成历史、简历版本、岗位收藏、分析历史查询、详情、删除和 2 到 5 条分析结果对比。
```

### Q16：为什么模型返回 JSON 还需要解析和校验？

回答：

```text
大模型虽然被要求返回 JSON，但仍可能带 Markdown 代码块、额外解释或字段缺失。

所以 FastAPI 会先从模型文本中提取 JSON，再用 Pydantic schema 做结构化解析。这样接口返回给 Spring Boot 的数据更稳定，测试也更容易覆盖。
```

### Q17：生成历史为什么用统一表？

回答：

```text
简历优化、面试准备和 STAR 答案都属于 AI 生成任务，它们有共同字段：userId、taskType、输入文本、结果 JSON 和 createdAt。

用统一的 job_generated_task 表，再用 taskType 区分 RESUME_OPTIMIZE、INTERVIEW_PREP 和 STAR_INTERVIEW_ANSWER，可以复用查询、详情和删除逻辑。
```

## 7. 测试和质量

### Q18：项目怎么保证质量？

回答：

```text
项目有三层质量保障。

第一，FastAPI pytest 覆盖路由、配置、RAG/Rerank 纯函数、fallback、评测工具、求职 Agent JSON 解析和接口行为。
第二，Spring Boot JUnit / MockMvc 覆盖 Controller、Service、权限边界、文档重复检测、聊天流程、求职 Agent 历史和静态页面入口。
第三，RAG 评测脚本用 30 条测试集评估检索命中、拒答准确率、引用有效率和引用支持率。
```

当前可以这样说：

```text
FastAPI pytest：128 passed
Spring Boot Maven test：100 passed
```

### Q19：提交前怎么检查？

回答：

```text
我做了 scripts/pre_submit_check.sh。

它会检查 .env、.venv、.DS_Store 是否被忽略，扫描疑似真实 DASHSCOPE_API_KEY，检查空白问题，验证 docker compose config，然后跑 FastAPI pytest 和 Spring Boot Maven test。
```

## 8. 部署和可复现

### Q20：Docker Compose 做了什么？

回答：

```text
Docker Compose 可以一键启动 MySQL、Qdrant、FastAPI 和 Spring Boot。

本地开发时也可以只用 Docker 启动 MySQL 和 Qdrant，然后在 IDE 里分别跑 FastAPI 和 Spring Boot。这样既能快速演示，也方便调试代码。
```

### Q21：为什么 `.env` 不提交？

回答：

```text
.env 里有真实 DASHSCOPE_API_KEY 和本地数据库密码，不能提交到 GitHub。

仓库里只提交 .env.example，里面放模板值。新电脑恢复项目时复制 .env.example 为 .env，再手动填写真实 Key。
```

## 9. 不足和下一步

### Q22：这个项目还有哪些不足？

回答：

```text
当前版本适合本地演示和简历展示，但还不是生产级系统。

不足包括：当前 JWT 仍是项目内账号体系，没有刷新/撤销和企业 SSO；还没有做大规模并发压测、监控告警、链路追踪、异步文档任务队列和对象存储。

当前已完成 Spring Security/JWT、Redis 限流、AI 调用日志和 AI 伴学 NDJSON 流式输出。下一步可以补异步任务、对象存储、指标监控、链路追踪和云服务器部署。
```

### Q23：如果只能讲一个技术难点，你讲什么？

回答：

```text
我会讲 RAG 的可靠性闭环。

因为它不只是把模型接进来，而是要处理文档切分、向量召回、Rerank 精排、拒答阈值、引用来源、fallback 和评测指标。每一步都会影响最终答案是否可信。

这个点能同时体现 AI 应用理解和工程质量意识。
```

### Q24：如果面试官问你本人主要收获是什么？

回答：

```text
我最大的收获是理解了 AI 应用不是单纯调用模型，而是要把模型能力放进一个有业务边界、数据边界、权限边界、质量评测和可复现环境的系统里。
```

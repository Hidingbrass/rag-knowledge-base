# 最终面试演示脚本

这个脚本用于把项目演示成一个完整作品，而不是零散功能集合。

演示主线：

```text
企业知识库 RAG
-> 权限和会话持久化
-> 求职辅助 Agent
-> 历史、对比、导出和简历版本
```

建议演示时间控制在 8 到 12 分钟。面试官如果只给 3 分钟，就讲第 1、2、5、8 步。

## 1. 开场说明

可以这样说：

```text
这个项目是一个 Spring Boot + FastAPI 的企业智能知识库 RAG 系统，并在同一套架构上扩展了求职辅助 Agent。

Spring Boot 负责业务后端，例如知识库、文档状态、权限、聊天会话、MySQL 持久化和前端接口。
FastAPI 负责 AI 服务，例如 PDF 解析、Chunk 切分、Embedding、Qdrant 检索、Rerank、Prompt 编排和通义千问调用。

MySQL 保存业务数据，Qdrant 保存向量数据。这个拆分可以避免把业务关系塞进向量库，也方便后续扩展不同 AI 场景。
```

重点表达：

```text
我不是只做了一个调用大模型的 Demo，而是把 RAG、权限、持久化、评测、前端演示和求职 Agent 扩展做成了完整闭环。
```

## 2. 启动方式

如果使用本地开发模式：

```bash
docker compose up -d mysql qdrant
source .venv/bin/activate
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
cd springboot-backend
mvn -s maven-settings.xml spring-boot:run
```

如果使用完整 Docker 演示：

```bash
docker compose up --build -d
```

打开页面：

```text
http://127.0.0.1:8080/index.html
```

可以这样说：

```text
开发时我更常用本地代码 + Docker 基础设施，方便调试 Java 和 Python。
演示时可以用 Docker Compose 一键启动完整系统，体现项目可复现性。
```

## 3. 演示企业知识库

操作顺序：

```text
1. 打开 Vue3 企业工作台。
2. 检查 Spring Boot 健康状态。
3. 创建或选择知识库。
4. 上传 `demo/technical_docs/pdfs/` 下六份企业技术 PDF。
5. 等文档状态变成 AVAILABLE。
```

讲解重点：

```text
上传资料后，Spring Boot 会先校验 PDF / Markdown / DOCX / TXT 白名单，保存文档记录和状态，计算文件 Hash 做重复上传检测，再调用 FastAPI。
FastAPI 按格式提取正文、切分 Chunk、调用通义千问 Embedding，并把向量写入 Qdrant。
处理完成后，Spring Boot 保存 fastApiDocumentId、chunkCount 和 AVAILABLE 状态。
```

可以强调字段边界：

```text
MySQL 中的 document id 是业务文档 ID。
FastAPI / Qdrant 中的 document_id 用于向量检索过滤。
这两个 ID 分开，是为了让业务数据和向量数据职责清晰。
```

补充演示资料管理：修改资料库名称或学习目标；删除资料库时确认关联向量、文档、会话和消息被一起清理；点击右上角个人信息修改昵称与学习方向。

## 4. 演示 RAG 问答

操作顺序：

```text
1. 创建聊天会话并选择 AVAILABLE 文档。
2. 输入“你好”，确认助手直接友好回复且没有伪造引用。
3. 输入“1+1 等于多少”，确认分类为开放域回答、没有引用且 retrievalMode 为 open_domain_chat。
4. 输入“今天合肥天气怎么样”，确认不进入 RAG，调用 `get_current_weather` 并显示 Open-Meteo 来源。
5. 输入“帮我删除这份知识库文档”，确认只出现待确认卡片；取消时文档保留，二次确认后才删除业务记录和向量。
6. 重新上传资料后输入一个资料内问题，查看 RAG 流式答案和引用来源。
7. 输入资料中没有答案的企业知识问题，确认系统仍进入 RAG 并严格拒答。
8. 刷新页面，确认不同路由的 USER / ASSISTANT 消息都能恢复。
```

讲解重点：

```text
正式问答入口在 Spring Boot 的会话接口。
Spring Boot 会先校验用户是否能访问这个会话和知识库，再校验 documentId 是否属于当前知识库，避免跨知识库越权检索。
对于纯问候、感谢和能力询问，Spring Boot 使用有限整句白名单直接回复并保存消息，不消耗模型调用。
Spring Boot 先处理写操作、企业知识和实时查询等确定性策略，再调用轻量分类器；企业知识和剩余分类异常进入 RAG，写操作与无工具实时查询不会错误落入 RAG。
包含实际知识问题的混合消息不会被问候关键词误截断。
```

RAG 链路可以这样讲：

```text
问题 -> 权限与文档归属校验 -> 确定性小聊 -> 企业知识保护 -> 轻量分类
  -> 纯小聊：确定性回复 -> 保存消息
  -> 企业知识/低置信/分类失败：Embedding -> Qdrant -> Rerank -> 阈值拒答 -> Prompt -> 引用校验
  -> 开放域/工具说明/澄清：受控普通聊天 -> 空引用 -> 用 retrievalMode 审计
```

这里不是“检索失败后切换通用聊天”：路由在检索前完成，任何企业知识都进入严格 RAG；一旦 RAG 拒答，不会再切换成无引用答案。

如果被问“如何减少幻觉”：

```text
第一，答案基于检索片段生成。
第二，返回引用来源，让答案可追溯。
第三，Rerank 后使用 rerank_min_score 做拒答，相关性不足时不让模型硬答。
第四，用评测脚本验证拒答准确率和引用支持率。
```

## 5. 演示权限和持久化

操作顺序：

```text
1. 注册或登录两个学习方向相同的账号。
2. 用账号 A 创建知识库，再验证账号 B 看不到列表且访问详情、文档和会话时返回 403。
3. 刷新页面后重新加载数据。
```

讲解重点：

```text
当前版本已经接入 Spring Security + JWT，业务接口默认强制登录。
旧版 userId + department 匿名联调只能通过配置显式开启。
权限判断放在 Spring Boot，不放在前端，因为前端参数不能被信任。
个人学习资料只允许 owner 访问；department 只是学习方向标签，不代表共享范围。
```

可以这样补充：

```text
聊天会话和消息已经落 MySQL，所以页面刷新或者服务重启后仍能恢复历史。
```

## 6. 演示求职辅助 Agent

操作顺序：

```text
1. 切到求职辅助 Agent。
2. 粘贴简历文本和岗位 JD。
3. 点击“解析简历”。
4. 点击“解析 JD”。
5. 点击“分析并保存”。
6. 查看匹配分、匹配技能、缺失技能、优势、风险、建议和面试题。
```

讲解重点：

```text
求职 Agent 没有重新设计一套架构，而是复用 Spring Boot + FastAPI 的边界。
FastAPI 负责 Prompt、模型调用和 JSON 解析。
Spring Boot 负责统一接口、userId、MySQL 历史和前端展示。
```

可以这样说：

```text
这说明这套架构不是只能做知识库问答，也可以扩展到更多 AI 应用场景。
```

## 7. 演示简历优化、面试准备和 STAR 答案

操作顺序：

```text
1. 点击“优化简历”，查看差距总结、改写建议、缺失关键词和行动项。
2. 点击“面试准备”，查看自我介绍、项目讲解、技术追问和行为问题。
3. 输入一个面试问题，例如“RAG 中如何解决幻觉问题？”。
4. 点击“生成 STAR 答案”。
5. 查看 S/T/A/R 拆解、完整口述答案、突出能力和可能追问。
```

讲解重点：

```text
简历优化、面试准备和 STAR 答案都属于 AI 生成物，所以统一保存到 job_generated_task 表。
taskType 区分 RESUME_OPTIMIZE、INTERVIEW_PREP 和 STAR_INTERVIEW_ANSWER。
这样生成历史的查看和删除逻辑可以复用。
```

如果面试官问为什么 STAR 单独做：

```text
面试准备包偏“准备清单”，STAR 答案偏“某个问题的可口述答案”。
它们业务目标不同，所以我没有把 STAR 塞进面试准备包，而是做成独立接口和展示面板。
```

## 8. 演示业务增强能力

操作顺序：

```text
1. 保存一个简历版本。
2. 使用、覆盖或删除简历版本。
3. 收藏当前岗位 JD。
4. 做多次求职分析。
5. 勾选 2 到 5 条历史记录，点击“对比选中”。
6. 导出求职分析、简历优化、面试准备或 STAR 答案 Markdown 报告。
```

讲解重点：

```text
这些能力不是大模型能力，而是业务产品能力。
例如简历版本、岗位收藏、历史对比和 Markdown 导出都完全在 Spring Boot + MySQL + 前端侧完成。
这体现的是业务建模、持久化和前端工作台能力。
```

## 9. 测试和质量

可以这样说：

```text
项目有两层自动化测试。
FastAPI 使用 pytest，覆盖 Prompt 构建、模型 JSON 解析、异常处理、RAG 工具函数和求职 Agent 能力。
Spring Boot 使用 JUnit 和 MockMvc，覆盖 Controller、Service、权限边界、数据库保存、静态页面入口和求职 Agent 历史。
```

当前结果：

```text
FastAPI pytest：128 passed
Spring Boot Maven test：100 passed
```

补充评测：

```text
RAG 侧保留 30 条历史单文稿回归集，并新增 26 条企业技术文档独立评测集，覆盖语义改写、精确配置名、跨文档和拒答问题。四路消融按 gold PDF 文件名评估 Hit@K、MRR、文档召回率和延迟。

2026-07-16 的真实实验已将 6 份 PDF 切成 40 个 Chunk。Vector、Sparse、Hybrid 的 Hit@6 和 Hybrid + Rerank 的 Hit@3 都是 1.0，MRR 分别是 0.95、0.8792、0.925、0.975。评测曾发现 Rerank Top 3 被同文档 Chunk 占满；我继续做真实 A/B，用候选排名与精排排名的文档级 RRF 把 Top 3 从 OPS/EVAL/EVAL 调整为 EVAL/OPS/SEC，跨文档 gold recall 从 0.5 修复为 1.0。我不会把这套小语料结果夸大成生产效果。
```

## 10. 结尾总结

可以这样收尾：

```text
这个项目对我来说重点不是页面效果，而是把 AI 应用做成一个有工程边界的系统。
RAG 侧体现 Embedding、向量检索、Rerank、拒答和评测。
Spring Boot 侧体现权限、状态、会话、MySQL 持久化和业务建模。
求职 Agent 侧体现同一套架构扩展新 AI 场景的能力。
```

## 11. 高频追问速答

### 为什么不用 LangChain？

```text
我这个阶段更想理解 RAG 底层链路，所以手动实现了解析、切分、Embedding、检索、Rerank、Prompt 和评测。
后续如果项目复杂度继续上升，可以再评估 LangChain 或 LlamaIndex 作为编排工具。
```

### MySQL 和 Qdrant 分别存什么？

```text
MySQL 存业务数据，例如知识库、文档状态、会话、消息、求职历史、简历版本和收藏岗位。
Qdrant 存文本 Chunk 向量和检索需要的 payload。
业务关系不放进 Qdrant，向量数据不放进 MySQL。
```

### 如果 FastAPI 调用失败怎么办？

```text
Spring Boot Client 层会把 RestClientException 转成业务异常。
文档入库流程会把文档状态更新成 FAILED。
RAG 侧也保留 fallback 和慢调用统计，方便定位是检索、Rerank 还是模型调用的问题。
```

### 为什么 AI 结果保存 resultJson？

```text
AI 返回字段比较多，而且后续可能调整。
MVP 阶段把完整结果保存成 resultJson，可以保留原始结构，方便回放和排查。
像 matchScore 这种稳定且高频展示的字段，则单独建列。
```

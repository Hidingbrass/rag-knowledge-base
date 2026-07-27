# 求职辅助 Agent 设计说明

## 1. 模块目标

求职辅助 Agent 是在企业知识库 RAG 项目基础上扩展出的第二个 AI 应用场景。

它的目标不是替代招聘系统，而是帮助用户把“自己的项目经历”和“岗位 JD”做结构化对比，输出匹配度、优势、风险、改进建议和面试题。

当前 MVP 已完成：

```text
简历文本 + 岗位 JD
-> FastAPI 构建求职分析 Prompt
-> 通义千问生成结构化 JSON
-> Spring Boot 返回分析结果
-> MySQL 保存求职分析历史
-> 前端演示页查询和展示历史记录
```

同时已补充两个结构化解析能力：

```text
简历文本 -> 目标岗位 / 技能 / 项目经历 / 优势 / 关键词
岗位 JD -> 岗位名称 / 级别 / 必备技能 / 加分技能 / 职责 / 要求 / 风险点
简历文本 + 岗位 JD -> 差距总结 / 改写建议 / 缺失关键词 / 行动项
简历文本 + 岗位 JD -> 自我介绍 / 项目讲解 / 技术追问 / 行为问题 / 准备清单
```

## 2. 整体链路

```text
浏览器前端
-> Spring Boot /api/job-agent/analyze
-> FastApiRagClient
-> FastAPI /job/analyze
-> qwen_service.chat_completion
-> 通义千问
-> FastAPI 解析 JSON
-> Spring Boot 保存 job_analysis_task
-> 前端展示结果
```

历史查询链路：

```text
浏览器前端
-> Spring Boot /api/job-agent/tasks?userId=demo-user
-> JobAgentService
-> JobAnalysisTaskRepository
-> MySQL job_analysis_task
-> JobAnalysisTaskResponse 列表
```

## 3. 服务边界

### FastAPI 负责 AI 能力

FastAPI 侧负责和模型相关的逻辑：

```text
1. 校验 resume_text 和 job_description 不能为空。
2. 构建求职分析 Prompt。
3. 调用通义千问 Chat。
4. 从模型返回中提取 JSON。
5. 解析成 JobAnalyzeResponse。
6. 提供简历结构化解析、JD 结构化解析和简历优化建议能力。
```

相关文件：

```text
app/schemas/job.py
app/services/job_service.py
app/api/job.py
tests/test_job_service.py
tests/test_routes_and_errors.py
```

这样设计的原因：

```text
Python 更适合承载 Prompt、模型调用、JSON 解析和 AI 评测逻辑。
FastAPI 已经承载 RAG、Rerank、Embedding 等 AI 能力，继续放求职 Agent 逻辑可以保持边界统一。
```

### Spring Boot 负责业务能力

Spring Boot 侧负责企业业务系统常见能力：

```text
1. 对外提供统一业务接口。
2. 接收前端 camelCase 请求。
3. 调用 FastAPI AI 服务。
4. 保存求职分析历史到 MySQL。
5. 按 userId 查询历史记录。
6. 给前端提供统一 ApiResponse 响应。
```

相关文件：

```text
springboot-backend/src/main/java/com/example/aikb/controller/JobAgentController.java
springboot-backend/src/main/java/com/example/aikb/service/JobAgentService.java
springboot-backend/src/main/java/com/example/aikb/client/FastApiRagClient.java
springboot-backend/src/main/java/com/example/aikb/entity/JobAnalysisTask.java
springboot-backend/src/main/java/com/example/aikb/repository/JobAnalysisTaskRepository.java
springboot-backend/src/test/java/com/example/aikb/controller/JobAgentControllerTests.java
```

这样设计的原因：

```text
Spring Boot 更适合承载用户、权限、数据库事务、历史记录和企业业务接口。
前端只需要调用 Spring Boot，不需要直接关心 FastAPI、模型服务或 DashScope Key。
```

## 4. 接口设计

### FastAPI 接口

```text
POST /job/analyze
POST /job/resume/parse
POST /job/jd/parse
POST /job/resume/optimize
POST /job/interview/prepare
```

请求体：

```json
{
  "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目...",
  "job_description": "岗位要求熟悉 Java、Spring Boot、MySQL、Python..."
}
```

简历结构化解析请求体：

```json
{
  "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目..."
}
```

简历结构化解析响应体：

```json
{
  "target_roles": ["Java 后端开发", "AI 应用开发"],
  "skills": ["Java", "Spring Boot", "FastAPI", "RAG"],
  "projects": [
    {
      "name": "企业知识库 RAG 系统",
      "role": "后端开发",
      "tech_stack": ["Spring Boot", "FastAPI", "Qdrant"],
      "description": "实现文档入库、向量检索和问答链路。",
      "highlights": ["完成端到端 RAG 链路"]
    }
  ],
  "work_experiences": [],
  "education": [],
  "certifications": [],
  "strengths": ["具备端到端项目经验"],
  "keywords": ["RAG", "向量数据库"]
}
```

JD 结构化解析请求体：

```json
{
  "job_description": "岗位要求熟悉 Java、Spring Boot、MySQL、Python..."
}
```

JD 结构化解析响应体：

```json
{
  "job_title": "Java 后端开发工程师",
  "seniority": "初中级",
  "required_skills": ["Java", "Spring Boot", "MySQL"],
  "preferred_skills": ["FastAPI", "RAG"],
  "responsibilities": ["参与后端接口开发", "参与 AI 应用落地"],
  "requirements": ["熟悉 Java Web 开发", "了解大模型调用"],
  "keywords": ["Java", "Spring Boot", "RAG"],
  "risks": ["未明确是否要求生产环境经验"]
}
```

简历优化建议请求体：

```json
{
  "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目...",
  "job_description": "岗位要求熟悉 Java、Spring Boot、MySQL、Python..."
}
```

简历优化建议响应体：

```json
{
  "summary": "强化 RAG 项目和 Java 后端能力表达。",
  "target_position": "Java 后端开发工程师",
  "gap_summary": ["生产环境经验体现不足"],
  "rewrite_suggestions": [
    {
      "section": "项目经历",
      "issue": "项目成果表达不够贴近 JD",
      "suggestion": "突出 Spring Boot、FastAPI、Qdrant 和大模型调用链路",
      "before_text": "我做过 RAG 项目。",
      "after_text": "基于 Spring Boot + FastAPI 构建企业知识库 RAG 系统，完成 PDF 入库、Embedding、Qdrant 检索、Rerank 和问答链路。",
      "keywords_added": ["Spring Boot", "FastAPI", "Qdrant", "RAG"]
    }
  ],
  "missing_keywords": ["Redis"],
  "action_items": ["准备说明接口容错和部署方案"]
}
```

面试准备包请求体：

```json
{
  "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目...",
  "job_description": "岗位要求熟悉 Java、Spring Boot、MySQL、Python..."
}
```

面试准备包响应体：

```json
{
  "target_position": "Java 后端开发工程师",
  "self_introduction": "面试官您好，我主要使用 Java 和 Python 做 AI 应用开发...",
  "project_talking_points": [
    {
      "project_name": "企业知识库 RAG 系统",
      "pitch": "我负责 Spring Boot 业务后端和 FastAPI AI 服务联调...",
      "technical_depth": ["文档切分策略", "Qdrant 向量检索", "Rerank 拒答阈值"],
      "likely_followups": ["Qdrant 和 MySQL 如何分工？"]
    }
  ],
  "technical_questions": [
    {
      "question": "RAG 中如何减少幻觉？",
      "answer_points": ["引用来源", "无依据拒答", "Rerank 重排"]
    }
  ],
  "behavioral_questions": [
    {
      "question": "项目中遇到过什么困难？",
      "answer_points": ["描述问题", "说明排查过程", "总结结果"]
    }
  ],
  "questions_to_ask": ["团队目前 AI 应用主要落在哪些业务场景？"],
  "preparation_checklist": ["复习 RAG 链路", "准备项目架构图"]
}
```

STAR 面试答案请求体：

```json
{
  "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目...",
  "job_description": "岗位要求熟悉 Java、Spring Boot、MySQL、Python...",
  "question": "RAG 中如何解决幻觉问题？"
}
```

STAR 面试答案响应体：

```json
{
  "target_position": "Java 后端开发工程师",
  "question": "RAG 中如何解决幻觉问题？",
  "situation": "项目需要基于企业文档回答问题，并避免无依据回答。",
  "task": "我负责让问答结果能基于检索片段生成，并在依据不足时拒答。",
  "action": ["保留引用来源", "接入 Rerank", "设置拒答阈值"],
  "result": "最终系统可以返回带来源的答案，并对低相关问题拒答。",
  "answer": "在我的 RAG 项目中，我主要从引用、重排和拒答三层控制幻觉。",
  "highlights": ["RAG 工程实践", "效果控制", "可解释性"],
  "follow_up_questions": ["拒答阈值如何确定？"]
}
```

响应体：

```json
{
  "match_score": 95,
  "matched_skills": ["Spring Boot", "MySQL", "FastAPI"],
  "missing_skills": [],
  "strengths": ["具备端到端 RAG 项目落地经验"],
  "risks": ["未明确提及生产环境部署经验"],
  "suggestions": ["准备说明 Qdrant 的选型原因"],
  "interview_questions": ["RAG 中如何解决幻觉问题？"]
}
```

### Spring Boot 分析接口

```text
POST /api/job-agent/analyze
```

请求体：

```json
{
  "userId": "demo-user",
  "resumeText": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目...",
  "jobDescription": "岗位要求熟悉 Java、Spring Boot、MySQL、Python..."
}
```

响应体：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "match_score": 95,
    "matched_skills": ["Spring Boot", "MySQL", "FastAPI"],
    "missing_skills": [],
    "strengths": ["具备端到端 RAG 项目落地经验"],
    "risks": ["未明确提及生产环境部署经验"],
    "suggestions": ["准备说明 Qdrant 的选型原因"],
    "interview_questions": ["RAG 中如何解决幻觉问题？"]
  }
}
```

### Spring Boot 结构化解析接口

```text
POST /api/job-agent/resume/parse
POST /api/job-agent/jd/parse
POST /api/job-agent/resume/optimize
POST /api/job-agent/interview/prepare
POST /api/job-agent/interview/star-answer
POST /api/job-agent/tasks/compare
GET /api/job-agent/generated-tasks?userId=demo-user
GET /api/job-agent/generated-tasks?userId=demo-user&taskType=RESUME_OPTIMIZE
GET /api/job-agent/generated-tasks/{taskId}?userId=demo-user
DELETE /api/job-agent/generated-tasks/{taskId}?userId=demo-user
POST /api/job-agent/favorites
GET /api/job-agent/favorites?userId=demo-user
GET /api/job-agent/favorites/{favoriteId}?userId=demo-user
DELETE /api/job-agent/favorites/{favoriteId}?userId=demo-user
```

请求体使用前端更习惯的 camelCase：

```json
{
  "resumeText": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目..."
}
```

```json
{
  "jobDescription": "岗位要求熟悉 Java、Spring Boot、MySQL、Python..."
}
```

```json
{
  "resumeText": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目...",
  "jobDescription": "岗位要求熟悉 Java、Spring Boot、MySQL、Python..."
}
```

Spring Boot 会转换成 FastAPI 需要的 snake_case，再把结构化结果、优化建议、面试准备包或 STAR 面试答案包装成统一 `ApiResponse` 返回给前端。

简历优化建议、面试准备包和 STAR 面试答案会额外保存到 MySQL 的 `job_generated_task` 表。它们共用一张生成历史表，通过 `taskType` 区分 `RESUME_OPTIMIZE`、`INTERVIEW_PREP` 和 `STAR_INTERVIEW_ANSWER`，完整模型结果保存到 `resultJson`，方便后续查看、删除和回放。

岗位收藏和分析结果对比属于 Spring Boot 业务能力：

```text
1. 岗位收藏保存到 MySQL 的 job_favorite 表。
2. 分析结果对比直接读取 job_analysis_task 和 resultJson，不重新调用大模型。
3. 对比接口复用 userId 归属校验，避免用户传入别人的 taskId。
4. 对比结果从 resultJson 中提取 matched_skills、missing_skills、strengths 和 risks。
```

### Spring Boot 历史查询接口

```text
GET /api/job-agent/tasks?userId=demo-user
```

响应体：

```json
{
  "success": true,
  "message": "ok",
  "data": [
    {
      "id": "uuid",
      "userId": "demo-user",
      "resumeText": "...",
      "jobDescription": "...",
      "matchScore": 95,
      "resultJson": "{...}",
      "createdAt": "2026-06-30T16:25:34.649843Z"
    }
  ]
}
```

### Spring Boot 历史详情接口

```text
GET /api/job-agent/tasks/{taskId}?userId=demo-user
```

处理规则：

```text
1. 先根据 taskId 查询 job_analysis_task。
2. 如果记录不存在，返回业务异常。
3. 如果记录存在，但 userId 和当前请求 userId 不一致，返回 403。
4. 校验通过后返回 JobAnalysisTaskResponse。
```

这个接口用于查看单条求职分析详情，同时防止用户通过猜测 taskId 查看别人的历史记录。

### Spring Boot 历史删除接口

```text
DELETE /api/job-agent/tasks/{taskId}?userId=demo-user
```

处理规则：

```text
1. 先复用 getOwnedTask(taskId, userId) 查询并校验记录归属。
2. 如果记录不存在，返回业务异常。
3. 如果记录存在，但 userId 和当前请求 userId 不一致，返回 403。
4. 校验通过后删除 job_analysis_task 记录。
```

删除接口复用详情接口的归属校验逻辑，避免出现“查询有权限判断，删除忘记判断”的问题。

## 5. 数据库设计

表名：

```text
job_analysis_task
```

核心字段：

```text
id               UUID 主键
user_id          用户 ID
resume_text      简历原文
job_description  岗位 JD 原文
match_score      匹配分
result_json      完整模型分析结果
created_at       创建时间
```

收藏岗位表：

```text
job_favorite
```

核心字段：

```text
id               UUID 主键
user_id          用户 ID
job_title        岗位名称
company_name     公司名称
job_description  岗位 JD 原文
source_url       来源链接
notes            备注
created_at       创建时间
```

`job_favorite` 只保存岗位本身，不保存分析结果。用户可以先收藏 JD，后续再把收藏 JD 放回输入区进行分析、优化或面试准备。

生成历史表：

```text
job_generated_task
```

核心字段：

```text
id               UUID 主键
user_id          用户 ID
task_type        生成类型，RESUME_OPTIMIZE、INTERVIEW_PREP 或 STAR_INTERVIEW_ANSWER
resume_text      简历原文
job_description  岗位 JD 原文
result_json      完整生成结果
created_at       创建时间
```

`job_generated_task` 用一张表保存简历优化建议、面试准备包和 STAR 面试答案，是因为这些数据的业务结构一致：都来自“简历 + JD + 模型生成结果”。差异放在 `task_type`，完整内容放在 `result_json`，避免为每一种生成结果单独建一套高度重复的表。

## 6. 为什么 resultJson 用 TEXT 保存

求职分析结果里包含多组数组字段：

```text
matched_skills
missing_skills
strengths
risks
suggestions
interview_questions
```

如果一开始就拆成多张子表，会让 MVP 复杂度明显上升。

当前阶段选择：

```text
完整结果保存成 resultJson TEXT
```

优点：

```text
1. 实现简单，能快速完成分析历史回放。
2. 保留模型原始结构，方便后续排查和复盘。
3. 模型响应字段后续调整时，不需要立刻改数据库表结构。
4. 前端可以直接解析 resultJson 展示完整分析结果。
```

后续如果要做统计，比如“最高频缺失技能”“用户常见风险项”，再把部分字段拆成独立表或 JSON 字段索引。

## 7. 为什么 matchScore 单独存字段

虽然完整结果已经存在 `resultJson` 里，但 `matchScore` 仍然单独保存。

原因是：

```text
1. 列表页经常需要直接展示匹配分。
2. 后续可以按 matchScore 排序或筛选。
3. 不需要每次查询都解析 resultJson。
4. 这个字段稳定、简单，适合单独建列。
```

这是一种常见折中：

```text
稳定且高频使用的字段单独建列；
复杂且可能变化的完整结果保存 JSON。
```

## 8. 测试设计

### FastAPI 测试

FastAPI 侧重点是 AI 服务逻辑：

```text
1. resume_text 不能为空。
2. job_description 不能为空。
3. Prompt 构建内容正确。
4. 模型返回 JSON 能被提取和解析。
5. /job/analyze、/job/resume/parse、/job/jd/parse、/job/resume/optimize、/job/interview/prepare、/job/interview/star-answer 路由正常注册。
```

当前 FastAPI 全量测试：

```text
128 passed
```

### Spring Boot 测试

Spring Boot 侧重点是业务链路和数据库保存：

```text
1. POST /api/job-agent/analyze 能返回分析结果。
2. 参数为空时返回 400。
3. 分析完成后会保存 JobAnalysisTask。
4. GET /api/job-agent/tasks 能按 userId 查询历史。
5. GET /api/job-agent/tasks/{taskId} 能查询单条历史详情。
6. 其他 userId 访问别人的 taskId 时返回 403。
7. DELETE /api/job-agent/tasks/{taskId} 能删除自己的历史记录。
8. 其他 userId 删除别人的 taskId 时返回 403，且数据库记录仍然存在。
9. POST /api/job-agent/resume/parse 能返回简历结构化结果。
10. POST /api/job-agent/jd/parse 能返回 JD 结构化结果。
11. POST /api/job-agent/resume/optimize 能返回简历优化建议。
12. POST /api/job-agent/interview/prepare 能返回面试准备包。
13. POST /api/job-agent/interview/star-answer 能返回 STAR 面试答案。
14. 优化建议、面试准备包和 STAR 面试答案生成后会保存 JobGeneratedTask。
15. GET /api/job-agent/generated-tasks 能按 userId 和 taskType 查询生成历史。
16. 生成历史支持详情和删除，并校验 userId 防止越权访问。
17. POST /api/job-agent/tasks/compare 能对比多条求职分析历史。
18. 收藏岗位支持创建、列表、详情和删除。
19. 简历版本支持创建、列表、详情、覆盖更新和删除。
20. 收藏岗位、简历版本和分析结果对比都校验 userId，防止越权访问。
21. Vue3 企业工作台包含求职 Agent 入口。
22. 原始联调页 debug.html 仍保留求职 Agent 调试入口。
```

当前 Spring Boot 全量测试：

```text
100 passed
```

## 9. 前端演示

Vue3 企业工作台：

```text
http://127.0.0.1:8080/index.html
```

原始联调页：

```text
http://127.0.0.1:8080/debug.html
```

已新增：

```text
5. 求职辅助 Agent
```

页面支持：

```text
1. 输入 userId。
2. 输入简历文本。
3. 输入岗位 JD。
4. 点击“解析简历”，查看目标岗位、技能、项目经历、优势和关键词。
5. 点击“解析 JD”，查看岗位名称、级别、必备技能、加分技能、职责、要求和风险点。
6. 点击“优化简历”，查看差距总结、改写建议、缺失关键词和行动项。
7. 点击“面试准备”，查看自我介绍、项目讲解、技术追问、行为问题、反问问题和准备清单。
8. 输入面试问题，点击“生成 STAR 答案”，查看 S/T/A/R 拆解、完整口述答案、突出能力和可能追问。
9. 在“生成历史”中查看或删除简历优化、面试准备和 STAR 答案记录。
10. 保存、使用、覆盖更新或删除不同岗位的简历版本。
11. 点击“分析并保存”。
12. 查看模型返回结果。
13. 点击“刷新历史”。
14. 从 MySQL 查询 job_analysis_task 历史记录。
15. 点击“查看详情”，调用详情接口并把完整结果展示到结果区域。
16. 点击“删除”，调用删除接口并刷新历史列表。
17. 收藏当前岗位 JD，后续可以从收藏列表重新使用 JD。
18. 勾选 2 到 5 条求职分析历史，点击“对比选中”，查看最佳匹配、平均分、共同匹配技能和共同缺失技能。
```

演示时可以先展示企业知识库 RAG，再展示求职 Agent，说明同一个双服务架构可以扩展到不同 AI 应用场景。

## 10. 面试讲法

可以这样讲：

```text
这个项目除了企业知识库 RAG，我还扩展了一个求职辅助 Agent。

用户输入简历文本和岗位 JD 后，Spring Boot 接收业务请求，并调用 FastAPI 的 /job/analyze。FastAPI 负责构建求职分析 Prompt，调用通义千问，然后把模型结果解析成结构化 JSON，包括匹配分、匹配技能、缺失技能、优势、风险、建议和面试题。

Spring Boot 拿到结果后，一方面直接返回给前端，另一方面把本次分析保存到 MySQL 的 job_analysis_task 表。表里会单独保存 matchScore，方便列表展示和排序；完整模型结果则保存成 resultJson，方便保留原始结构和后续回放。

另外，简历结构化解析、JD 结构化解析、简历优化建议、面试准备包和 STAR 面试答案也复用同一条边界：FastAPI 负责 Prompt 和模型 JSON 解析，Spring Boot 负责统一接口和请求字段转换，前端负责把结果分区展示。优化建议、面试准备包和 STAR 面试答案会保存到 `job_generated_task`，方便用户后续回看。简历版本、岗位收藏和分析结果对比则完全放在 Spring Boot + MySQL 侧，因为它们是业务数据管理和历史数据计算，不需要再次调用模型；前端会把对比结果展示成决策面板，突出最佳岗位、平均分、共同技能、共同缺失、分数条、优势和风险，并支持把求职分析、简历优化、面试准备包和 STAR 面试答案导出为 Markdown 报告。
```

## 11. 后续扩展方向

可以继续做：

```text
1. 面试演示脚本和项目讲解材料继续打磨。
2. 简历项目版描述和 GitHub 展示口径继续统一。
3. 后续可以扩展 PDF / DOCX 报告导出。
```

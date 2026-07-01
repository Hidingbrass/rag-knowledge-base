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
```

请求体：

```json
{
  "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目...",
  "job_description": "岗位要求熟悉 Java、Spring Boot、MySQL、Python..."
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
5. /job/analyze 路由正常注册。
```

当前 FastAPI 全量测试：

```text
72 passed
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
7. 静态演示页包含求职 Agent 入口。
```

当前 Spring Boot 全量测试：

```text
27 passed
```

## 9. 前端演示

静态演示页：

```text
http://127.0.0.1:8080/index.html
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
4. 点击“分析并保存”。
5. 查看模型返回结果。
6. 点击“刷新历史”。
7. 从 MySQL 查询 job_analysis_task 历史记录。
```

演示时可以先展示企业知识库 RAG，再展示求职 Agent，说明同一个双服务架构可以扩展到不同 AI 应用场景。

## 10. 面试讲法

可以这样讲：

```text
这个项目除了企业知识库 RAG，我还扩展了一个求职辅助 Agent。

用户输入简历文本和岗位 JD 后，Spring Boot 接收业务请求，并调用 FastAPI 的 /job/analyze。FastAPI 负责构建求职分析 Prompt，调用通义千问，然后把模型结果解析成结构化 JSON，包括匹配分、匹配技能、缺失技能、优势、风险、建议和面试题。

Spring Boot 拿到结果后，一方面直接返回给前端，另一方面把本次分析保存到 MySQL 的 job_analysis_task 表。表里会单独保存 matchScore，方便列表展示和排序；完整模型结果则保存成 resultJson，方便保留原始结构和后续回放。

这样设计的好处是边界清晰：Python FastAPI 负责 AI 能力，Java Spring Boot 负责业务接口、数据库和历史记录。后续如果要加简历优化、面试题生成、删除历史记录、收藏岗位，都可以在这个结构上继续扩展。
```

## 11. 后续扩展方向

可以继续做：

```text
1. DELETE /api/job-agent/tasks/{taskId} 删除历史记录。
2. 简历结构化解析：提取技能、项目、年限、学历等。
3. JD 结构化解析：提取岗位技能、职责、加分项。
4. 简历优化建议：根据 JD 生成更匹配的项目表述。
5. 面试准备包：生成自我介绍、项目讲解、常见追问。
6. 前端分区展示 strengths、risks、suggestions 和 interview_questions。
```

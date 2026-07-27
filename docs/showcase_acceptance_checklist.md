# 项目展示验收清单

这个清单用于确认项目已经达到“可以放到简历和 GitHub 展示”的状态。它不是开发任务列表，而是验收证据列表：每一项都要能被页面、接口、测试、文档或命令证明。

## 1. 展示定位

项目展示时建议使用这个主线：

```text
企业知识库 RAG
-> Spring Boot 业务闭环
-> FastAPI AI 链路
-> MySQL / Qdrant 数据边界
-> Rerank、拒答、引用和评测
-> 求职辅助 Agent 扩展
-> Docker Compose 可复现启动
```

一句话介绍：

```text
这是一个 Spring Boot + FastAPI 的企业知识库 RAG 系统，并在同一套架构上扩展了求职辅助 Agent。项目重点是把 AI 调用做成可持久化、可评测、可复现、可展示的工程系统。
```

## 2. 必须能证明的能力

| 能力 | 证明方式 | 验收标准 |
| --- | --- | --- |
| 完整启动 | `docker compose up --build -d` 或本地开发启动 | Spring Boot、FastAPI、MySQL、Qdrant 都能正常启动 |
| 健康检查 | Spring `/api/health` + `scripts/smoke_check.sh` 内网检查 FastAPI | 接口返回正常状态 |
| 多格式入库 | 分别上传 PDF、Markdown、DOCX 或 TXT | 支持格式可解析，文档状态从处理中变为 `AVAILABLE` |
| 资料库管理 | 修改后删除本人资料库 | 修改立即生效；删除时关联向量、文档、会话和消息一起清理 |
| 个人资料 | 修改昵称和学习方向 | 页面立即更新，并签发包含最新资料的新 JWT |
| RAG 问答 | 前端创建会话并提问 | 返回答案和引用来源 |
| 拒答能力 | 提问与文档无关的问题 | 系统不强行编造答案 |
| 权限控制 | 两个同学习方向 JWT 账号交叉访问 | 非 owner 不能访问个人知识库、文档或会话 |
| 会话持久化 | 刷新页面或重启服务 | 历史会话和消息仍能从 MySQL 读取 |
| 求职分析 | 前端或 `scripts/demo_job_agent.sh` | 返回匹配分、技能、风险、建议和面试题，并保存历史 |
| 简历/JD 解析 | 前端求职 Agent 区域 | 能提取结构化技能、项目、关键词和风险点 |
| 简历优化 | 前端或接口 | 生成差距总结、改写建议、缺失关键词和行动项 |
| 面试准备 | 前端或接口 | 生成自我介绍、项目讲解、技术追问和准备清单 |
| STAR 答案 | 前端或接口 | 生成 S/T/A/R 拆解和完整口述答案 |
| 历史对比 | 勾选 2 到 5 条分析历史 | 输出最佳匹配、平均分、共同匹配技能和共同缺失技能 |
| 自动化回归 | `bash scripts/pre_submit_check.sh` | FastAPI 与 Spring Boot 测试通过 |
| 密钥安全 | `git status`、`git check-ignore`、提交前检查脚本 | `.env` 和真实 API Key 不进入 Git |

## 3. 面试前推荐验收顺序

先做不依赖真实模型的检查：

```bash
bash scripts/pre_submit_check.sh
```

再做完整运行检查：

```bash
docker compose up --build -d
docker compose ps
bash scripts/smoke_check.sh
```

检查服务：

```bash
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:6333/collections
bash scripts/smoke_check.sh
```

最后做页面演示：

```text
http://127.0.0.1:8080/index.html
```

如果只想快速验证求职 Agent 的后端闭环：

```bash
bash scripts/demo_job_agent.sh
```

注意：求职 Agent 演示脚本会调用真实 FastAPI 和 DashScope，并把演示数据写入 MySQL。

`scripts/smoke_check.sh` 只做服务可用性检查，不调用 DashScope，也不写入演示数据。

## 4. 推荐截图清单

这些截图适合放到 README、作品集或面试演示 PPT 中：

```text
1. Vue3 企业工作台首页和健康状态
2. 知识库列表和文档上传状态
3. RAG 问答结果和引用来源
4. 权限切换后的 403 或无权限提示
5. 求职 Agent 匹配分析结果
6. 简历优化建议
7. 面试准备包或 STAR 答案
8. 历史记录、对比面板或 Markdown 导出
9. 本地开发模式下带 `X-API-Key` 授权的 FastAPI Swagger 页面
10. Docker Compose 容器运行状态
```

截图的目标不是追求花哨，而是让别人一眼看到：

```text
系统真的能跑
数据真的能保存
AI 链路真的能调用
权限和测试真的有覆盖
```

## 5. 面试讲解重点

建议重点讲 5 件事：

```text
1. 为什么用 Spring Boot + FastAPI 双后端，而不是全放在一个服务里。
2. 为什么 MySQL 保存业务数据，Qdrant 保存向量数据。
3. 文档上传后，状态、文件 Hash、FastAPI 入库和 Qdrant 向量之间如何协作。
4. RAG 中如何通过 Rerank、阈值拒答、引用来源和评测减少幻觉。
5. 求职 Agent 如何复用同一套架构扩展新 AI 场景。
```

如果面试官追问“这个项目最大的工程价值是什么”，可以这样回答：

```text
它不是只把大模型接口接到页面上，而是把 AI 能力放进一个有业务建模、权限控制、持久化、评测、测试和 Docker 可复现环境的系统里。
```

## 6. 不要夸大的边界

当前项目可以说：

```text
支持本地完整演示
支持 Docker Compose 一键启动
支持权限模拟、历史持久化、RAG 评测和求职 Agent 工作流
```

当前项目不要说：

```text
已经达到生产级高可用
已经做了大规模并发压测
已经接入真实企业统一认证
已经支持多租户计费或灰度发布
```

如果被问到这些方向，可以回答：

```text
当前版本重点是学习和简历展示，已经完成 Spring Security/JWT、Redis 限流、AI 调用成功率/P95/Token/成本聚合，并把服务边界、数据边界、权限边界和测试边界打好。生产化下一步会补 OpenTelemetry、异步任务队列、对象存储和部署流水线。
```

## 7. 当前可展示结论

在提交前检查通过、真实 `.env` 可用、Docker Desktop 正常运行的前提下，这个项目已经具备简历展示需要的核心证据：

```text
功能闭环：知识库 RAG + 求职 Agent
工程闭环：Spring Boot + FastAPI + MySQL + Qdrant
质量闭环：pytest + JUnit + RAG 评测
演示闭环：Vue3 工作台 + Docker Compose + 命令行 demo
文档闭环：启动、迁移、演示、简历材料和提交检查
```

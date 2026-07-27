# 项目展示证据报告

这份文档用于把项目的“可展示证据”集中到一起。它回答一个面试官可能会关心的问题：这个项目不是只写了功能介绍，而是有哪些可以被验证的工程结果。

## 1. 当前展示结论

当前项目已经具备简历展示所需的核心证据：

```text
功能闭环：企业知识库 RAG + 求职辅助 Agent
工程闭环：Spring Boot + FastAPI + MySQL + Qdrant
质量闭环：pytest + JUnit + GitHub Actions CI
演示闭环：Vue3 工作台 + Docker Compose + 命令行 demo
文档闭环：启动、迁移、演示、验收、面试问答和简历材料
```

更适合面试中的表达：

```text
这个项目不是只调用大模型接口，而是把 RAG 和求职 Agent 放进一个有业务边界、数据边界、权限边界、质量检查和可复现启动方式的工程系统里。
```

## 2. 自动化质量证据

本地提交前检查：

```bash
bash scripts/pre_submit_check.sh
# 或者
make pre-submit
```

检查内容：

```text
1. .env / .venv / .DS_Store 是否被 .gitignore 忽略
2. 是否有疑似真实 DASHSCOPE_API_KEY 被提交
3. Git diff 是否存在空白错误
4. README 和 docs 的本地 Markdown 链接是否有效
5. docker compose config 是否可解析
6. Vue3 Vitest 和 Vite build 是否通过
7. FastAPI pytest 是否通过
8. Spring Boot Maven test 是否通过
```

当前已验证结果：

```text
Markdown local links passed
Vue3 Vitest：4 passed
FastAPI pytest：128 passed
Spring Boot Maven test：102 passed
Pre-submit checks passed
```

GitHub Actions CI：

```text
工作流文件：.github/workflows/ci.yml
触发方式：push / pull_request 到 main
运行内容：安装 Python 依赖、配置 Java 21、复制 .env.example 为 .env、运行 scripts/pre_submit_check.sh
当前状态：main 分支 CI 已通过
```

## 3. 功能展示证据

企业知识库 RAG 可展示能力：

```text
1. 创建知识库
2. 上传 PDF、Markdown、DOCX 或 TXT
3. 文档状态流转到 AVAILABLE
4. 创建聊天会话
5. 指定文档提问
6. 返回答案和引用来源
7. 无相关资料时拒答
8. 切换用户身份验证权限边界
9. 刷新页面后从 MySQL 恢复会话和消息
10. 编辑资料库和个人资料，并安全删除资料库关联数据
```

求职辅助 Agent 可展示能力：

```text
1. 简历与岗位 JD 匹配分析
2. 简历结构化解析
3. JD 结构化解析
4. 简历优化建议
5. 面试准备包
6. STAR 面试答案
7. 生成历史保存和查询
8. 简历版本管理
9. 岗位收藏
10. 多条分析结果对比
11. Markdown 报告导出
```

命令行快速演示：

```bash
bash scripts/demo_job_agent.sh
```

注意：命令行求职 Agent 演示会调用真实 FastAPI 和 DashScope，并把结果写入 MySQL。

服务启动后的快速冒烟检查：

```bash
bash scripts/smoke_check.sh
# 或者
make smoke
```

这个脚本只检查 Spring Boot、FastAPI、Qdrant、Vue3 工作台和 debug 页是否可访问，不调用 DashScope，也不写入 demo 数据。

## 4. 运行和复现证据

推荐开发启动方式：

```bash
docker compose up -d mysql qdrant
source .venv/bin/activate
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
cd springboot-backend
mvn -s maven-settings.xml spring-boot:run
```

完整 Docker Compose 启动方式：

```bash
cp .env.example .env
docker compose up --build -d
# 或者
make docker-up
```

启动后检查：

```text
Vue3 企业工作台: http://127.0.0.1:8080/index.html
Spring Boot 健康检查: http://127.0.0.1:8080/api/health
FastAPI: 仅 Compose 内网访问，由 Spring Boot 携带 X-API-Key 调用
Qdrant 控制台: http://127.0.0.1:6333/dashboard
```

也可以直接执行：

```bash
bash scripts/smoke_check.sh
```

## 5. 文档证据

核心展示文档：

```text
README.md：项目入口、架构图、功能列表、启动方式和 CI 徽章
docs/showcase_acceptance_checklist.md：项目展示验收清单
docs/final_demo_script.md：最终面试演示脚本
docs/architecture_decisions.md：架构决策记录
docs/interview_full_project_qa.md：完整项目高频面试问答
docs/resume_full_project.md：完整项目简历材料
docs/startup_guide.md：启动与部署说明
docs/pre_submit_checklist.md：提交前检查清单
```

这些文档分别解决：

```text
给面试官看：README、展示验收清单、演示脚本、架构决策记录
给自己练习：高频面试问答、简历材料
给环境复现：启动说明、新电脑迁移指南、提交前检查清单
```

## 6. 当前边界

可以明确说：

```text
项目支持本地完整演示、Docker Compose 一键启动、GitHub Actions 自动回归、MySQL 持久化、Qdrant 向量检索、RAG 评测和求职 Agent 工作流。

企业知识库主演示不再依赖单份测试文稿：仓库内置 6 份基于真实代码整理的技术 PDF 和 26 条 gold document 评测题，可复现比较 Vector、Sparse、Hybrid RRF 与 Hybrid RRF + Rerank。

2026-07-16 已使用真实 DashScope 与 Qdrant 完成在线消融：6 份 PDF 共 40 个 Chunk，Vector、Sparse、Hybrid 的 Hit@6 和 Hybrid + Rerank 的 Hit@3 均为 1.0；MRR 分别为 0.95、0.8792、0.925、0.975。实验先记录了跨文档 gold recall 降为 0.5 的失败案例，再用候选排名 + 精排排名的文档级 RRF 将其修复为 1.0，保留了完整的失败、诊断、修改和复测证据。
```

不要夸大为：

```text
生产级高可用系统
已经完成大规模并发压测
已经接入真实企业统一认证
已经完成云端生产部署
```

如果被追问生产化方向，可以回答：

```text
当前版本重点是学习、简历展示和工程闭环。已经完成 Spring Security/JWT、Redis 限流、AI 调用成功率/P95/Token/成本聚合、受控重试与单进程熔断；生产化下一步会补异步任务队列、对象存储、OpenTelemetry 链路追踪和云服务器部署。
```

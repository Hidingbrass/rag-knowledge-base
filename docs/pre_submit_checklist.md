# GitHub 提交前检查清单

这个清单用于每次提交前确认项目处于可展示状态。

推荐先运行一键检查脚本：

```bash
bash scripts/pre_submit_check.sh
```

脚本会依次检查本地忽略规则、疑似密钥、空白问题、Docker Compose 配置、FastAPI 测试和 Spring Boot 测试。下面的分项说明用于你想手动排查时参考。

## 1. 密钥和本地数据

必须确认：

```text
.env 不提交
真实 DASHSCOPE_API_KEY 不提交
MySQL 数据目录不提交
Qdrant 本地数据目录不提交
构建产物不提交
IDE 本地配置不提交
```

推荐命令：

```bash
git status --short
git check-ignore -v .env .venv .DS_Store
git diff -- .env.example
git diff --check
```

检查 `.env.example` 时只允许出现模板值，例如：

```env
DASHSCOPE_API_KEY=your_dashscope_api_key_here
```

不要出现真实 Key。

## 2. FastAPI 回归

在项目根目录执行：

```bash
source .venv/bin/activate
python -m pytest -q
```

当前预期：

```text
96 passed
```

这组测试不应依赖真实通义千问调用。测试里会用 Mock 或只测纯函数逻辑。

## 3. Spring Boot 回归

在 Spring Boot 目录执行：

```bash
cd springboot-backend
mvn -s maven-settings.xml test
```

当前预期：

```text
57 passed
```

Spring Boot 测试使用 H2 和 MockBean，不依赖本地 MySQL、Qdrant 或真实 FastAPI。

## 4. Docker Compose 配置检查

在项目根目录执行：

```bash
docker compose config --quiet
```

注意：

```text
docker compose config 会读取 .env。
不要把完整 config 输出贴到公开平台，因为它可能展开真实环境变量。
```

如果命令无输出且退出码为 0，说明 Compose 文件语法和变量解析通过。

## 5. 本地演示检查

开发模式至少检查：

```text
http://127.0.0.1:8000/health
http://127.0.0.1:8000/docs
http://127.0.0.1:8080/api/health
http://127.0.0.1:8080/index.html
```

完整 Docker 模式至少检查：

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f api
docker compose logs -f backend
```

演示页需要确认：

```text
可以创建或读取知识库
可以上传 PDF 并看到文档状态
可以创建会话并提问
求职 Agent 页面可以看到简历解析、JD 解析、分析、优化、面试准备、STAR 答案、历史、对比和导出入口
```

命令行演示可以运行：

```bash
bash scripts/demo_job_agent.sh
```

这一步会调用真实 AI 服务，适合在 `.env` 已配置真实 `DASHSCOPE_API_KEY` 且 Spring Boot / FastAPI 都启动后执行。

## 6. 文档同步检查

这些文档需要和当前功能保持一致：

```text
README.md
docs/startup_guide.md
docs/job_agent_design.md
docs/resume_full_project.md
docs/final_demo_script.md
docs/pre_submit_checklist.md
```

如果新增接口，至少同步：

```text
README API 列表
job_agent_design 接口设计
resume_full_project 简历材料
final_demo_script 演示流程
```

## 7. 推荐提交顺序

提交前先查看变更：

```bash
git status --short
git diff --stat
```

推荐提交信息格式：

```text
feat: add star interview answer workflow
docs: add final demo script and submit checklist
test: cover job agent generated task history
```

如果一次改动包含功能、测试、文档，可以用一个概括性提交：

```text
feat: polish job agent showcase workflow
```

## 8. 面试展示前最后确认

演示前确认：

```text
Docker Desktop 已启动
.env 里 DASHSCOPE_API_KEY 是真实可用 Key
MySQL 端口 3307 没被占用
Qdrant 端口 6333 没被占用
FastAPI 端口 8000 没被占用
Spring Boot 端口 8080 没被占用
浏览器能打开 http://127.0.0.1:8080/index.html
```

如果面试现场网络不稳定：

```text
优先演示页面、历史数据、文档和代码结构。
真实模型调用失败时，解释项目依赖 DashScope 网络和 API Key，自动化测试用 Mock 保证代码链路可回归。
```

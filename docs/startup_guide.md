# 启动与部署说明

这个文档记录当前完整项目的启动方式：

```text
Spring Boot + FastAPI + Qdrant + MySQL + 通义千问
```

推荐开发启动方式：

```text
Docker Compose 启动 MySQL / Qdrant
-> 本地虚拟环境启动 FastAPI
-> Maven 启动 Spring Boot
-> 浏览器打开静态演示页
```

这样最适合学习和调试：数据库和向量库交给 Docker，业务代码在本地 IDE 里跑。

## 1. 环境变量

项目使用 `.env` 管理本地环境变量。

第一次运行时，在项目根目录复制模板：

```powershell
cd D:\pycharm\rag-knowledge-base
Copy-Item .env.example .env
```

然后打开 `.env`，填写真实 DashScope API Key：

```env
DASHSCOPE_API_KEY=你的真实 API Key
```

注意：

- `.env` 保存真实密钥，不要提交到 Git。
- `.env.example` 只保存模板，可以提交。
- 本地 FastAPI 会读取 `.env`。
- Docker Compose 中的 FastAPI 容器也会读取 `.env`。
- Docker Compose 中的 MySQL 会读取 `MYSQL_ROOT_PASSWORD`、`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`。
- Spring Boot 的 MySQL 配置可以通过 `MYSQL_URL`、`MYSQL_USER`、`MYSQL_PASSWORD` 覆盖。
- `docker compose config` 会把 `.env` 中的真实值展开显示，排查配置时可以用，但不要把完整输出发到公开平台。

## 2. 推荐启动方式：本地代码 + Docker 基础设施

### 2.1 启动 MySQL 和 Qdrant

在项目根目录执行：

```powershell
cd D:\pycharm\rag-knowledge-base
docker compose up -d mysql qdrant
```

当前端口：

```text
MySQL: 127.0.0.1:3307
Qdrant: 127.0.0.1:6333
```

MySQL 默认配置来自 `.env`：

```env
MYSQL_DATABASE=rag_knowledge_base
MYSQL_USER=rag_user
MYSQL_PASSWORD=rag_password
```

检查端口：

```powershell
netstat -ano | findstr ":3307"
netstat -ano | findstr ":6333"
```

检查 Qdrant：

```powershell
curl.exe http://127.0.0.1:6333/collections
```

为什么先启动它们：

```text
Spring Boot 启动时要连接 MySQL。
FastAPI 文档入库时要连接 Qdrant。
```

### 2.2 启动 FastAPI

新开一个 PowerShell：

```powershell
cd D:\pycharm\rag-knowledge-base
.\.venv\Scripts\activate
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

检查：

```powershell
curl.exe http://127.0.0.1:8000/health
curl.exe http://127.0.0.1:8000/qdrant/health
```

浏览器访问：

```text
http://127.0.0.1:8000/docs
```

FastAPI 负责：

- PDF 解析
- 文本切分
- Embedding
- Qdrant 入库
- Vector / Hybrid 检索
- qwen3-rerank
- 通义千问生成回答

### 2.3 启动 Spring Boot

再新开一个 PowerShell：

```powershell
cd D:\pycharm\rag-knowledge-base\springboot-backend
mvn -s maven-settings.xml spring-boot:run
```

检查：

```powershell
curl.exe http://127.0.0.1:8080/api/health
curl.exe http://127.0.0.1:8080/api/knowledge-bases
```

Spring Boot 负责：

- 知识库
- 文档状态
- 重复上传检测
- 用户权限
- 聊天会话
- 聊天消息
- 调用 FastAPI
- MySQL 持久化

### 2.4 打开前端演示页

浏览器访问：

```text
http://127.0.0.1:8080/index.html
```

如果页面没有显示最新内容：

```text
Ctrl + F5 强制刷新
```

面试演示脚本见：

```text
docs/day3_frontend_demo_script.md
```

## 3. 一键启动 FastAPI 容器方式

如果只想验证 FastAPI + Qdrant，或者想测试 Docker 构建，可以执行：

```powershell
cd D:\pycharm\rag-knowledge-base
docker compose up --build -d qdrant api
```

查看日志：

```powershell
docker compose logs -f api
docker compose logs -f qdrant
```

注意：

```text
api 容器中的 QDRANT_URL 会被 docker-compose.yml 覆盖为 http://qdrant:6333。
本地 Python 启动 FastAPI 时，QDRANT_URL 使用 http://127.0.0.1:6333。
```

如果要同时启动 MySQL：

```powershell
docker compose up --build -d mysql qdrant api
```

## 4. 端口说明

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| Spring Boot | 8080 | 业务后端和静态演示页 |
| FastAPI | 8000 | AI 服务和 Swagger |
| Qdrant HTTP | 6333 | 向量数据库 API |
| Qdrant gRPC | 6334 | Qdrant gRPC |
| MySQL | 3307 | Spring Boot 业务数据库 |

为什么 MySQL 用 3307：

```text
很多电脑本地已经安装 MySQL，占用了 3306。
Docker Compose 把容器内 3306 映射到宿主机 3307，可以减少端口冲突。
```

## 5. 常用验证命令

### 5.1 检查 Spring Boot

```powershell
curl.exe http://127.0.0.1:8080/api/health
```

### 5.2 检查知识库列表

```powershell
curl.exe http://127.0.0.1:8080/api/knowledge-bases
```

### 5.3 检查 FastAPI

```powershell
curl.exe http://127.0.0.1:8000/health
```

### 5.4 检查 Qdrant

```powershell
curl.exe http://127.0.0.1:6333/collections
```

### 5.5 检查端口

```powershell
netstat -ano | findstr ":8080"
netstat -ano | findstr ":8000"
netstat -ano | findstr ":6333"
netstat -ano | findstr ":3307"
```

## 6. 自动化测试

### 6.1 FastAPI 测试

在项目根目录：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

当前测试覆盖：

- 路由注册
- 统一异常响应
- 配置默认值
- Qdrant source 字段
- Rerank index 回填
- Rerank fallback
- 评测工具函数
- 评测 CLI 参数解析
- vector/hybrid 检索模式对比

### 6.2 Spring Boot 测试

在 Spring Boot 目录：

```powershell
cd D:\pycharm\rag-knowledge-base\springboot-backend
mvn -s maven-settings.xml test
```

当前测试覆盖：

- Spring Boot 启动
- KnowledgeBase Service
- Document Service
- Chat Service
- 文档权限 Controller
- 聊天权限 Controller
- 旧版 RAG 接口停用
- 静态演示页 `/index.html`
- `/favicon.ico` 不再误报 500

## 7. 评测脚本

评测脚本会调用真实 Embedding、Rerank 和 Chat API。

运行前确认：

- `.env` 中 `DASHSCOPE_API_KEY` 有效。
- Qdrant 已启动。
- FastAPI 已启动。
- 测试 PDF 已经完成向量入库。

常用命令：

```powershell
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_retrieval
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_rerank --mode single
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_rerank --mode comparison
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_generation
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_generation_rerank --mode single
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_generation_rerank --mode comparison
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_rerank_params
```

## 8. 常见问题

### 8.1 Spring Boot 启动失败：MySQL 连接不上

典型错误：

```text
Communications link failure
Connection refused
```

原因：

```text
127.0.0.1:3307 没有 MySQL 服务。
```

处理：

```powershell
docker compose up -d mysql
netstat -ano | findstr ":3307"
```

### 8.2 上传 PDF 后状态是 FAILED

常见原因：

```text
FastAPI 没启动
Qdrant 没启动
DASHSCOPE_API_KEY 无效
模型接口网络失败
```

处理：

```powershell
curl.exe http://127.0.0.1:8000/health
curl.exe http://127.0.0.1:8000/qdrant/health
```

然后重新上传 PDF。

注意：

```text
FAILED 记录会保存在 MySQL 中，方便前端展示失败原因。
FAILED 记录不会被重复上传检测复用，可以修复服务后重新上传。
```

### 8.3 页面看不到最新改动

处理：

```text
浏览器按 Ctrl + F5 强制刷新。
```

如果 Spring Boot 还在运行，修改静态资源后建议重启 Spring Boot。

### 8.4 端口被占用

检查：

```powershell
netstat -ano | findstr ":8080"
netstat -ano | findstr ":8000"
netstat -ano | findstr ":3307"
netstat -ano | findstr ":6333"
```

处理方式：

```text
关闭占用端口的进程，或者修改 application.yml / docker-compose.yml 中的端口。
```

### 8.5 未找到 DASHSCOPE_API_KEY

现象：

```text
RuntimeError: 未找到 DASHSCOPE_API_KEY，请检查 .env 文件
```

处理：

```powershell
Copy-Item .env.example .env
```

然后填写真实 API Key。

### 8.6 Docker Compose 中 FastAPI 访问不到 Qdrant

本地 Python 运行时：

```env
QDRANT_URL=http://127.0.0.1:6333
```

Docker Compose 运行时：

```env
QDRANT_URL=http://qdrant:6333
```

`docker-compose.yml` 已经自动覆盖这个值，一般不需要手动改。

### 8.7 需要清空数据

只停止容器，不删除数据：

```powershell
docker compose down
```

停止容器并删除 MySQL / Qdrant 数据卷：

```powershell
docker compose down -v
```

谨慎使用 `-v`，它会删除已上传文档、知识库、聊天记录和向量数据。

## 9. 面试前 Checklist

演示前按顺序确认：

```text
[ ] .env 已配置 DASHSCOPE_API_KEY
[ ] docker compose up -d mysql qdrant 已执行
[ ] 3307 / 6333 端口正常
[ ] FastAPI 已启动，/health 正常
[ ] Spring Boot 已启动，/api/health 正常
[ ] http://127.0.0.1:8080/index.html 能打开
[ ] 页面能从 MySQL 恢复知识库、文档和会话
[ ] 至少有一个 AVAILABLE 文档
[ ] 可以发送一个 RAG 问题
[ ] 可以切换无权限用户并看到 403 提示
```

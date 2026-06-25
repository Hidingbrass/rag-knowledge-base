# 启动与部署说明

这个文档记录当前 FastAPI + Qdrant + 通义千问 RAG 学习项目的启动方式。

项目现在支持两种运行方式：

```text
方式一：本地 Python 虚拟环境 + 本地 Qdrant
方式二：Docker Compose 同时启动 FastAPI 和 Qdrant
```

## 1. 环境变量

项目使用 `.env` 管理本地环境变量。

第一次运行时，可以复制模板：

```powershell
Copy-Item .env.example .env
```

然后打开 `.env`，填写你的通义千问 API Key：

```env
DASHSCOPE_API_KEY=你的真实 API Key
```

注意：

- `.env` 保存真实密钥，不要提交到 Git。
- `.env.example` 只保存模板，可以提交。
- Docker Compose 会读取 `.env`，并额外把容器内的 `QDRANT_URL` 覆盖为 `http://qdrant:6333`。

## 2. 本地 Python 方式启动

适合开发和调试代码。

### 2.1 创建并安装依赖

如果已经有 `.venv`，可以跳过创建步骤。

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

### 2.2 启动 Qdrant

如果本机已安装 Docker，可以只用 Docker 启动 Qdrant：

```powershell
docker run -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant:v1.14.1
```

### 2.3 启动 FastAPI

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

启动后访问：

```text
Swagger: http://127.0.0.1:8000/docs
健康检查: http://127.0.0.1:8000/health
Qdrant 检查: http://127.0.0.1:8000/qdrant/health
```

## 3. Docker Compose 方式启动

适合验证“别人拿到项目后能否一键启动”。

### 3.1 启动

```powershell
docker compose up --build
```

启动后访问：

```text
Swagger: http://127.0.0.1:8000/docs
FastAPI 健康检查: http://127.0.0.1:8000/health
Qdrant 控制台/API: http://127.0.0.1:6333
```

### 3.2 后台启动

```powershell
docker compose up --build -d
```

查看日志：

```powershell
docker compose logs -f api
docker compose logs -f qdrant
```

停止服务：

```powershell
docker compose down
```

如果想连 Qdrant 数据一起删除：

```powershell
docker compose down -v
```

## 4. 常用接口验证

### 4.1 健康检查

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

### 4.2 Qdrant 健康检查

```powershell
Invoke-RestMethod http://127.0.0.1:8000/qdrant/health
```

### 4.3 上传 PDF 入库

推荐先用 Swagger 页面测试：

```text
http://127.0.0.1:8000/docs
```

接口：

```text
POST /documents/index
```

上传项目中的测试文档：

```text
rag-learning-test-document.pdf
```

### 4.4 RAG 问答

无 Rerank：

```text
POST /rag/chat
```

带 Rerank：

```text
POST /rag/chat/rerank
```

推荐参数：

```json
{
  "question": "RAG 的英文全称是什么？",
  "candidate_k": 6,
  "rerank_top_k": 3,
  "rerank_min_score": 0.75
}
```

## 5. 自动化测试

运行 pytest：

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

当前结果：

```text
67 passed
```

## 6. 评测脚本

运行 Rerank 生成评测：

```powershell
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_retrieval
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_rerank --mode single
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_rerank --mode comparison
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_generation
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_generation --top-k 5 --min-score 0.5
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_generation_rerank --mode single
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_generation_rerank --mode comparison
```

运行 Rerank 参数组合实验：

```powershell
.\.venv\Scripts\python.exe -m app.evaluation.evaluate_rerank_params
```

注意：

- 评测脚本会调用真实 Embedding、Rerank 和 Chat API。
- 运行前需要确认 `.env` 中的 `DASHSCOPE_API_KEY` 有效。
- 运行前需要先上传测试 PDF 并完成向量入库。

## 7. 常见问题

### 7.1 未找到 DASHSCOPE_API_KEY

现象：

```text
RuntimeError: 未找到 DASHSCOPE_API_KEY，请检查 .env 文件
```

处理：

```powershell
Copy-Item .env.example .env
```

然后填写真实 API Key。

### 7.2 FastAPI 容器访问不到 Qdrant

本地 Python 运行时：

```env
QDRANT_URL=http://127.0.0.1:6333
```

Docker Compose 运行时：

```env
QDRANT_URL=http://qdrant:6333
```

`docker-compose.yml` 已经自动覆盖这个值，一般不需要手动改。

### 7.3 修改代码后 Docker 没生效

重新构建：

```powershell
docker compose up --build
```

### 7.4 需要清空 Qdrant 数据

如果是 Docker Compose：

```powershell
docker compose down -v
docker compose up --build
```

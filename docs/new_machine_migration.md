# 新电脑恢复项目指南

这个文档解决一个实际问题：

```text
如果换一台新的 Windows / Mac，GitHub 上的代码怎么恢复成可运行项目？
```

先记住一句话：

```text
GitHub 主要保存代码、配置模板和文档。
本地运行产生的数据，例如 MySQL 表数据、Qdrant 向量数据、.env 密钥，不会自动跟着代码走。
```

## 1. 新电脑需要安装什么

### 1.1 通用依赖

```text
Git
Python 3.11+
Docker Desktop
JDK 21
Maven 3.9+
IDE：PyCharm / IntelliJ IDEA / VS Code 均可
```

### 1.2 Windows 建议

```text
PowerShell
Docker Desktop
JDK 21
Maven
Python 3.11
```

### 1.3 Mac 建议

```bash
brew install git python@3.11 maven openjdk@21
```

Docker Desktop for Mac 需要单独安装。

## 2. 拉取代码

```bash
git clone https://github.com/Hidingbrass/rag-knowledge-base.git
cd rag-knowledge-base
```

如果你使用的是自己的 fork 或其他仓库地址，把上面的 URL 换成实际地址。

## 3. 创建环境变量文件

项目不会提交真实 `.env`，所以新电脑需要重新创建：

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

Mac / Linux：

```bash
cp .env.example .env
```

然后打开 `.env`，填写真实 DashScope API Key：

```env
DASHSCOPE_API_KEY=你的真实 API Key
```

注意：

- `.env` 是本机私密配置，不要提交到 Git。
- `.env.example` 是模板，可以提交。
- `docker compose config` 会展开 `.env` 中的真实值，不要把完整输出发到公开平台。

## 4. 启动 MySQL 和 Qdrant

推荐开发模式只用 Docker 启动基础设施：

```bash
docker compose up -d mysql qdrant
```

启动后端口是：

```text
MySQL: 127.0.0.1:3307
Qdrant: 127.0.0.1:6333
```

为什么 MySQL 用 3307：

```text
很多电脑本机已经有 MySQL，占用了 3306。
所以本项目把容器内的 3306 映射到宿主机的 3307，减少端口冲突。
```

## 5. 启动 FastAPI

Windows PowerShell：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

Mac / Linux：

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

检查：

```text
http://127.0.0.1:8000/health
http://127.0.0.1:8000/docs
```

## 6. 启动 Spring Boot

```bash
cd springboot-backend
mvn -s maven-settings.xml spring-boot:run
```

如果你在 Mac 上没有这个 `maven-settings.xml`，可以先尝试：

```bash
mvn spring-boot:run
```

启动后访问：

```text
http://127.0.0.1:8080/index.html
```

## 7. 运行测试

FastAPI 测试：

```bash
pytest
```

Spring Boot 测试：

```bash
cd springboot-backend
mvn -s maven-settings.xml test
```

如果没有本地 Maven settings：

```bash
mvn test
```

## 8. 原来的数据怎么办

这里要区分三类东西。

### 8.1 代码和文档

这些会跟着 GitHub 走：

```text
FastAPI 代码
Spring Boot 代码
静态前端页面
测试代码
README 和 docs 文档
Dockerfile / docker-compose.yml / .env.example
```

### 8.2 密钥和本地配置

这些不会跟着 GitHub 走，需要新电脑重新创建：

```text
.env
DASHSCOPE_API_KEY
本机数据库密码覆盖配置
IDE 本地配置
```

### 8.3 MySQL 和 Qdrant 运行数据

这些默认存在旧电脑 Docker volume 里，不会自动上传到 GitHub：

```text
知识库记录
文档状态
聊天会话
聊天消息
Qdrant 向量数据
```

学习阶段推荐做法：

```text
新电脑重新上传测试 PDF，让系统重新解析、切分、向量化。
```

项目展示阶段推荐做法：

```text
准备一份固定演示 PDF。
启动项目后上传一次。
然后用前端演示知识库、文档、会话、问答、权限控制。
```

生产环境做法：

```text
MySQL 使用数据库备份恢复。
Qdrant 使用 snapshot 或服务端持久化存储恢复。
密钥放到服务器环境变量或密钥管理服务中。
```

## 9. 新电脑恢复后的验证顺序

按下面顺序验证，最容易定位问题：

```text
1. docker compose up -d mysql qdrant
2. 访问 http://127.0.0.1:6333/collections，确认 Qdrant 正常
3. 启动 FastAPI
4. 访问 http://127.0.0.1:8000/health
5. 启动 Spring Boot
6. 访问 http://127.0.0.1:8080/index.html
7. 前端点击检查服务状态
8. 创建知识库
9. 上传 PDF
10. 创建会话并提问
```

## 10. 面试时怎么解释

可以这样讲：

```text
这个项目把代码、配置模板和运行数据分开管理。
代码通过 GitHub 管理，.env 不提交，避免泄露 API Key。
MySQL 和 Qdrant 通过 Docker Compose 启动，保证新环境能快速复现。
本地数据不进入 Git，如果要迁移生产数据，需要使用 MySQL 备份和 Qdrant snapshot。
```

这个回答体现的是工程意识：

```text
代码可复现
配置不泄露
数据可迁移
开发环境和运行数据分离
```

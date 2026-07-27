# AIKB Docker 部署与故障排查

- 文档编号：AIKB-OPS-004
- 编排方式：Docker Compose
- 核心服务：mysql、qdrant、redis、api、backend

## 1. 端口与容器通信

默认宿主机端口为：Spring Boot 8080、FastAPI 8000、Qdrant HTTP 6333、Qdrant gRPC 6334、MySQL
3307、Redis 6379。MySQL、Redis 和 Spring Boot 的宿主机端口分别可以用 MYSQL_HOST_PORT、
REDIS_HOST_PORT、BACKEND_HOST_PORT 覆盖。

容器之间不使用宿主机 127.0.0.1。backend 通过 mysql:3306、redis:6379 和 http://api:8000 通信；
api 通过 http://qdrant:6333 访问向量数据库。宿主机端口变化不会影响这些 Compose 内部地址。

## 2. 8080 端口冲突

如果 docker compose up 报错 bind: address already in use，先用 lsof -nP -iTCP:8080 -sTCP:LISTEN
确认占用者。不要为了启动当前项目直接停止不相关服务。可在 .env 设置 BACKEND_HOST_PORT=8081，重新
启动 backend，然后通过 http://127.0.0.1:8081/index.html 访问工作台。

修改源码或环境变量后，仅执行 docker compose up -d 可能继续使用旧镜像。需要运行
docker compose up -d --build api backend 重新构建。浏览器仍显示旧页面时，再执行强制刷新并确认访问的
端口确实属于新容器，而不是宿主机上另一个 Java 进程。

## 3. 启动依赖与健康检查

mysql 和 redis 配置了健康检查，api 配置了 /health 检查。backend 在 mysql、redis 和 api 满足依赖
条件后启动。查看状态使用 docker compose ps -a；查看启动原因使用 docker compose logs --tail=120
backend api。

Spring Boot 健康接口是 /api/health，FastAPI 健康接口是 /health，Qdrant Collection 列表可以通过
/collections 查看。健康接口成功只说明进程与基础依赖可用，不代表 DashScope 模型调用和完整 RAG
问答已经通过。

## 4. Qdrant Collection 排障

当前 Hybrid 结构要求 Collection 同时包含 dense 和 sparse 命名向量。默认名称是
rag_chunks_hybrid_v1。如果旧 Collection 是未命名的单 Dense 向量，不能在原结构上直接执行新的命名
向量 upsert。测试数据无需保留时，优先切换新 Collection 并重新上传 PDF。

切换 Collection 后，新 Collection 初始 points_count 为 0。MySQL 可能仍展示旧文档元数据，但旧
document_id 在新 Collection 中没有 Chunk。演示时应新建知识库并重新上传技术文档，确认状态 AVAILABLE
和 chunk_count 后再执行问答与评测。

## 5. 常见故障顺序

访问 8080 失败时先检查端口监听与 docker compose ps；backend 反复重启时查看 Flyway、MySQL 连接和
环境变量日志；上传失败时查看 MySQL 文档状态与 error_message，再检查 FastAPI 日志、DashScope Key、
Qdrant schema 和 Redis 限流；问答无结果时先确认 Collection points_count 与 document_id，再看检索
分数，不要直接把问题归因于大模型。

任何会删除 volume、Collection 或业务记录的操作都属于破坏性操作，执行前必须确认数据是否需要保留。

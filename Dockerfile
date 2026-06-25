# FastAPI RAG 服务镜像。
#
# 这个镜像只负责运行 Python/FastAPI 服务。
# Qdrant 不放进同一个镜像，而是由 docker-compose.yml 单独启动一个 qdrant 服务。

FROM python:3.12-slim

# 让 Python 日志实时输出到控制台，并减少无意义的 .pyc 缓存文件。
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

# 先复制 requirements.txt，可以利用 Docker 构建缓存。
# 只有依赖变化时才重新安装依赖。
COPY requirements.txt .

RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# 复制应用代码。
COPY app ./app

EXPOSE 8000

# 容器启动后运行 FastAPI。
# 0.0.0.0 表示允许容器外部访问，端口由 docker-compose 映射到宿主机。
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

# 知途 AI Web 应用镜像。
#
# 第一阶段用 Node 构建 Vue3 前端，第二阶段用 Maven 打包包含前端产物的后端，
# 最后一阶段只保留运行所需的 JRE 和 jar。

FROM node:22-alpine AS frontend-build

WORKDIR /workspace/frontend

# 先安装锁定版本的依赖，充分利用 Docker 层缓存。
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# 先复制 Maven 配置和 pom.xml，尽量利用 Docker 构建缓存。
COPY maven-settings.xml pom.xml ./
RUN mvn -s maven-settings.xml -DskipTests dependency:go-offline

# 再复制后端源码，并用前端阶段的生产产物覆盖静态资源。
COPY src ./src
COPY --from=frontend-build /workspace/src/main/resources/static ./src/main/resources/static
RUN mvn -s maven-settings.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/ai-knowledge-base-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

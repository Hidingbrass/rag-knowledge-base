# Spring Boot 业务后端镜像。
#
# 第一阶段用 Maven + JDK 21 编译项目，第二阶段只保留运行所需的 JRE 和 jar。
# 这样最终镜像更小，也不需要用户在宿主机安装 Maven 才能用 Docker 启动后端。

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# 先复制 Maven 配置和 pom.xml，尽量利用 Docker 构建缓存。
COPY maven-settings.xml pom.xml ./
RUN mvn -s maven-settings.xml -DskipTests dependency:go-offline

# 再复制源码并打包。
COPY src ./src
RUN mvn -s maven-settings.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/ai-knowledge-base-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

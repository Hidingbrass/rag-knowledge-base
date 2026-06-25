package com.example.aikb;

import com.example.aikb.config.FastApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot 业务服务启动入口。
 *
 * 项目二中，Spring Boot 不直接做 PDF 解析、Embedding 或 Rerank。
 * 它负责企业业务层：用户、知识库、文档状态、聊天记录，以及通过 HTTP 调用 FastAPI AI 服务。
 */
@SpringBootApplication
@EnableConfigurationProperties(FastApiProperties.class)
public class AiKnowledgeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKnowledgeBaseApplication.class, args);
    }
}

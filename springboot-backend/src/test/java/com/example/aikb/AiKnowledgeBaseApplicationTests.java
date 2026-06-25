package com.example.aikb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring Boot 应用启动测试。
 *
 * 这个测试不会真正调用 FastAPI，也不会连接 Qdrant。
 * 它的作用是检查 Spring Boot 项目的基础装配是否正确：
 * 1. application.yml 能不能被 Spring 读取；
 * 2. FastApiProperties 这种配置类能不能成功绑定；
 * 3. Controller、Client、ExceptionHandler 等 Bean 能不能被 Spring 创建；
 * 4. 项目结构、包名、依赖是否存在明显错误。
 *
 * 面试时可以这样讲：
 * 这是一个最小集成测试，用来保证后端服务的启动链路没有坏。
 */
@SpringBootTest
class AiKnowledgeBaseApplicationTests {

    @Test
    void contextLoads() {
        // 方法体可以为空。
        // 只要 Spring Boot 上下文能启动成功，这个测试就会通过。
    }
}

package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Spring Boot 健康检查接口。
 *
 * 这个接口只检查 Java 业务服务是否正常启动，
 * 不代表 FastAPI、Qdrant 或模型服务一定可用。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of(
                "service", "springboot-backend",
                "status", "ok"
        ));
    }
}

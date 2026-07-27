package com.example.aikb.dto.fastapi;

/**
 * 让统一 FastAPI Client 无需反射即可提取模型用量。
 */
public interface FastApiUsageCarrier {
    FastApiModelUsage modelUsage();
}

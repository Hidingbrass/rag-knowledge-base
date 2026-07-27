package com.example.aikb.service;

import com.example.aikb.config.UploadLockProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 基于 Redis 的上传防重复提交锁。
 *
 * 这个锁只保护“短时间内同一用户重复提交同一文件”的并发窗口。
 * 真正长期的重复文件复用仍然由 MySQL 中的 file_hash + status 判断负责。
 */
@Service
public class UploadDuplicateLockService {

    private static final Logger log = LoggerFactory.getLogger(UploadDuplicateLockService.class);

    private final StringRedisTemplate redisTemplate;
    private final UploadLockProperties properties;

    public UploadDuplicateLockService(StringRedisTemplate redisTemplate, UploadLockProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean tryLockDocumentUpload(String userId, UUID knowledgeBaseId, String fileHash) {
        if (!properties.enabled()) {
            return true;
        }

        String key = "lock:upload:document:%s:%s:%s".formatted(userId, knowledgeBaseId, fileHash);
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    "1",
                    Duration.ofSeconds(Math.max(1, properties.ttlSeconds()))
            );
            return Boolean.TRUE.equals(acquired);
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis upload lock failed, allow upload. userId={}, knowledgeBaseId={}, message={}",
                    userId, knowledgeBaseId, exception.getMessage());
            return true;
        } catch (RuntimeException exception) {
            log.warn("Unexpected upload lock failure, allow upload. userId={}, knowledgeBaseId={}, message={}",
                    userId, knowledgeBaseId, exception.getMessage());
            return true;
        }
    }

    public void unlockDocumentUpload(String userId, UUID knowledgeBaseId, String fileHash) {
        if (!properties.enabled()) {
            return;
        }

        String key = "lock:upload:document:%s:%s:%s".formatted(userId, knowledgeBaseId, fileHash);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Release upload lock failed. userId={}, knowledgeBaseId={}, message={}",
                    userId, knowledgeBaseId, exception.getMessage());
        }
    }
}

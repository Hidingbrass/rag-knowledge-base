package com.example.aikb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upload.lock")
public record UploadLockProperties(
        boolean enabled,
        long ttlSeconds
) {
}

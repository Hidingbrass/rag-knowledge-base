package com.example.aikb.service;

import com.example.aikb.entity.AiCallLog;
import com.example.aikb.repository.AiCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AiCallLogService {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final AiCallLogRepository repository;

    public AiCallLogService(AiCallLogRepository repository) {
        this.repository = repository;
    }

    public void recordFastApiCall(
            String businessType,
            String endpoint,
            boolean success,
            long elapsedMs,
            String errorMessage
    ) {
        try {
            repository.save(new AiCallLog(
                    UUID.randomUUID(),
                    "FASTAPI",
                    businessType,
                    endpoint,
                    success,
                    elapsedMs,
                    truncate(errorMessage),
                    Instant.now()
            ));
        } catch (RuntimeException exception) {
            log.warn("Failed to save AI call log: businessType={}, endpoint={}", businessType, endpoint, exception);
        }
    }

    public List<AiCallLog> listRecentLogs() {
        return repository.findTop20ByOrderByCreatedAtDesc();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH);
    }
}

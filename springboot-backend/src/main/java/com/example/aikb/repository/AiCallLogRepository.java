package com.example.aikb.repository;

import com.example.aikb.entity.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, UUID> {
    List<AiCallLog> findTop20ByOrderByCreatedAtDesc();
    List<AiCallLog> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant createdAt);
}

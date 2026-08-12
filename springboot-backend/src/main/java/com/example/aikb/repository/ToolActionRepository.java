package com.example.aikb.repository;

import com.example.aikb.entity.ToolAction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ToolActionRepository extends JpaRepository<ToolAction, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select action from ToolAction action where action.id = :id")
    Optional<ToolAction> findByIdForUpdate(@Param("id") UUID id);

    void deleteBySessionIdIn(List<UUID> sessionIds);
}

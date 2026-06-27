package com.example.aikb.repository;

import com.example.aikb.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 聊天会话 JPA Repository。
 *
 * 用于保存和查询 chat_session 表。
 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findByUserIdOrderByCreatedAtDesc(String userId);
}

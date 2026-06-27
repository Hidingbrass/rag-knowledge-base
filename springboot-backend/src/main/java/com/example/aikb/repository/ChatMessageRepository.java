package com.example.aikb.repository;

import com.example.aikb.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 聊天消息 JPA Repository。
 *
 * 查询消息时按 createdAt 正序排列，前端展示出来就是正常聊天顺序：
 * 先显示用户问题，再显示 AI 回答。
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}

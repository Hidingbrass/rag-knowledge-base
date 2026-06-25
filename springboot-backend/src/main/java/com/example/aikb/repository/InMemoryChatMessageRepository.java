package com.example.aikb.repository;

import com.example.aikb.entity.ChatMessage;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 聊天消息内存仓库。
 *
 * 当前阶段先用 ConcurrentHashMap 模拟数据库表。
 * 后续接 MySQL 时，这个类会替换成 JPA Repository。
 *
 * 类比数据库：
 * - storage 可以理解成 chat_message 表。
 * - key 是消息 id。
 * - value 是一条 ChatMessage 记录。
 */
@Repository
public class InMemoryChatMessageRepository {

    /**
     * 临时保存所有聊天消息。
     *
     * 用户问题和 AI 回答都会存在这里，
     * 通过 ChatMessage.role 区分 USER 和 ASSISTANT。
     */
    private final ConcurrentMap<UUID, ChatMessage> storage = new ConcurrentHashMap<>();

    /**
     * 保存一条消息。
     *
     * 用户提问时会保存一条 USER 消息；
     * FastAPI 返回答案后会保存一条 ASSISTANT 消息。
     */
    public ChatMessage save(ChatMessage message) {
        storage.put(message.id(), message);
        return message;
    }

    /**
     * 查询某个会话下的所有消息。
     *
     * 按 createdAt 正序排列：
     * 旧消息在前，新消息在后，前端展示时就是正常聊天顺序。
     */
    public List<ChatMessage> findBySessionId(UUID sessionId) {
        return new ArrayList<>(storage.values())
                .stream()
                .filter(message -> message.sessionId().equals(sessionId))
                .sorted(Comparator.comparing(ChatMessage::createdAt))
                .toList();
    }
}

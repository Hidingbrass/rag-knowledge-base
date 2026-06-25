package com.example.aikb.repository;

import com.example.aikb.entity.ChatSession;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 聊天会话内存仓库。
 *
 * 当前阶段先用 ConcurrentHashMap 模拟数据库表。
 * 后续接 MySQL 时，这个类会替换成 JPA Repository。
 *
 * 类比数据库：
 * - storage 可以理解成 chat_session 表。
 * - key 是会话 id。
 * - value 是一条 ChatSession 记录。
 */
@Repository
public class InMemoryChatSessionRepository {

    /**
     * 临时保存所有聊天会话。
     *
     * 使用 ConcurrentMap 的原因：
     * Spring Boot 服务可能同时处理多个请求，ConcurrentHashMap 比普通 HashMap 更适合并发场景。
     */
    private final ConcurrentMap<UUID, ChatSession> storage = new ConcurrentHashMap<>();

    /**
     * 保存或覆盖一个会话。
     *
     * 如果 id 不存在，就是新增；
     * 如果 id 已存在，就是覆盖旧记录。
     */
    public ChatSession save(ChatSession session) {
        storage.put(session.id(), session);
        return session;
    }

    /**
     * 按会话 id 查询。
     *
     * 返回 Optional 是为了表达“可能查不到”，避免直接返回 null。
     */
    public Optional<ChatSession> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    /**
     * 查询某个用户创建的所有会话。
     *
     * 按 createdAt 倒序排列：
     * 最新创建的会话排在最前面，更符合前端历史会话列表的展示习惯。
     */
    public List<ChatSession> findByUserId(String userId) {
        return new ArrayList<>(storage.values())
                .stream()
                .filter(session -> session.userId().equals(userId))
                .sorted(Comparator.comparing(ChatSession::createdAt).reversed())
                .toList();
    }
}

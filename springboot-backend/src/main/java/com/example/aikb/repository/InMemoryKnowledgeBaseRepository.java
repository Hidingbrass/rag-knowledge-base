package com.example.aikb.repository;

import com.example.aikb.entity.KnowledgeBase;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 知识库内存仓库。
 *
 * 这是 MVP 阶段的临时实现，用 ConcurrentHashMap 模拟数据库。
 * 后续接 MySQL 时，这一层会替换成 Spring Data JPA Repository。
 *
 * 好处：
 * Controller 和 Service 现在就能按真实分层写；
 * 将来换数据库时，业务流程不用大改。
 */
@Repository
public class InMemoryKnowledgeBaseRepository {

    private final ConcurrentMap<UUID, KnowledgeBase> storage = new ConcurrentHashMap<>();

    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        storage.put(knowledgeBase.id(), knowledgeBase);
        return knowledgeBase;
    }

    public Optional<KnowledgeBase> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    public List<KnowledgeBase> findAll() {
        return new ArrayList<>(storage.values())
                .stream()
                .sorted(Comparator.comparing(KnowledgeBase::createdAt).reversed())
                .toList();
    }
}

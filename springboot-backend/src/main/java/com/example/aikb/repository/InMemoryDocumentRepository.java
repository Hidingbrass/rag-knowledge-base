package com.example.aikb.repository;

import com.example.aikb.entity.KnowledgeDocument;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文档内存仓库。
 *
 * 当前用内存保存文档状态，方便我们先完成端到端流程。
 * 真正接 MySQL 时，这里的 save/findById/findByKnowledgeBaseId 会变成数据库查询。
 */
@Repository
public class InMemoryDocumentRepository {

    private final ConcurrentMap<UUID, KnowledgeDocument> storage = new ConcurrentHashMap<>();

    public KnowledgeDocument save(KnowledgeDocument document) {
        storage.put(document.id(), document);
        return document;
    }

    public Optional<KnowledgeDocument> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    public List<KnowledgeDocument> findByKnowledgeBaseId(UUID knowledgeBaseId) {
        return new ArrayList<>(storage.values())
                .stream()
                .filter(document -> document.knowledgeBaseId().equals(knowledgeBaseId))
                .sorted(Comparator.comparing(KnowledgeDocument::createdAt).reversed())
                .toList();
    }
}

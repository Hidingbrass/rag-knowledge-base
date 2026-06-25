package com.example.aikb.service;

import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.repository.InMemoryKnowledgeBaseRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 知识库业务服务。
 *
 * Service 层负责业务规则：
 * - 创建知识库时生成业务 ID 和创建时间；
 * - 查询知识库不存在时抛出业务异常；
 * - 后续权限校验也会放在这里，而不是散落在 Controller。
 */
@Service
public class KnowledgeBaseService {

    private final InMemoryKnowledgeBaseRepository repository;

    public KnowledgeBaseService(InMemoryKnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    public KnowledgeBase create(CreateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase(
                UUID.randomUUID(),
                request.name(),
                request.description(),
                request.ownerId(),
                request.department(),
                Instant.now()
        );

        return repository.save(knowledgeBase);
    }

    public KnowledgeBase getRequired(UUID knowledgeBaseId) {
        return repository.findById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException("知识库不存在: " + knowledgeBaseId));
    }

    public List<KnowledgeBase> list() {
        return repository.findAll();
    }
}

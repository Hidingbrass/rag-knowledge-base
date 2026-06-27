package com.example.aikb.service;

import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import com.example.aikb.repository.KnowledgeBaseRepository;
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
 * - 统一校验当前用户是否能访问某个知识库。
 *
 * Controller 不直接写权限判断，避免同一条规则散落在多个 HTTP 接口里。
 */
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseService(KnowledgeBaseRepository repository) {
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

    /**
     * 查询知识库，并校验当前用户是否有访问权限。
     *
     * 当前学习版规则：
     * - 创建者可以访问自己创建的知识库；
     * - 同部门用户可以访问同部门知识库；
     * - 既不是创建者，也不是同部门用户时拒绝访问。
     */
    public KnowledgeBase getRequiredWithAccess(UUID knowledgeBaseId, String userId, String department) {
        KnowledgeBase knowledgeBase = getRequired(knowledgeBaseId);
        boolean isOwner = knowledgeBase.ownerId().equals(userId);
        boolean isSameDepartment = knowledgeBase.department().equals(department);
        if (!isOwner && !isSameDepartment) {
            throw new ForbiddenException("无权访问知识库: " + knowledgeBaseId);
        }
        return knowledgeBase;
    }

    public List<KnowledgeBase> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }
}

package com.example.aikb.service;

import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.repository.InMemoryKnowledgeBaseRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KnowledgeBaseService 单元测试。
 *
 * 单元测试的目标不是测 Spring Boot 能不能启动，
 * 而是直接验证某个业务类的规则是否正确。
 */
class KnowledgeBaseServiceTests {

    @Test
    void createShouldSaveKnowledgeBase() {
        KnowledgeBaseService service = new KnowledgeBaseService(
                new InMemoryKnowledgeBaseRepository()
        );

        KnowledgeBase knowledgeBase = service.create(new CreateKnowledgeBaseRequest(
                "研发知识库",
                "保存研发制度和项目文档",
                "user-1",
                "研发部"
        ));

        assertThat(knowledgeBase.id()).isNotNull();
        assertThat(knowledgeBase.name()).isEqualTo("研发知识库");
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void getRequiredShouldThrowWhenKnowledgeBaseNotFound() {
        KnowledgeBaseService service = new KnowledgeBaseService(
                new InMemoryKnowledgeBaseRepository()
        );

        assertThatThrownBy(() -> service.getRequired(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识库不存在");
    }
}

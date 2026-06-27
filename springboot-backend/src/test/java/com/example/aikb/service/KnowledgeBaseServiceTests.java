package com.example.aikb.service;

import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KnowledgeBaseService 集成测试。
 *
 * 这里不再手动 new 内存仓库，而是启动 Spring 容器，让 Service 使用 JPA Repository。
 * 测试环境会连接 H2 内存数据库，用来验证知识库记录可以真正写入和查询。
 */
@SpringBootTest
@Transactional
class KnowledgeBaseServiceTests {

    @Autowired
    private KnowledgeBaseService service;

    @Test
    void createShouldSaveKnowledgeBase() {
        KnowledgeBase knowledgeBase = service.create(new CreateKnowledgeBaseRequest(
                "研发知识库",
                "保存研发制度和项目文档",
                "user-1",
                "研发部"
        ));

        assertThat(knowledgeBase.id()).isNotNull();
        assertThat(knowledgeBase.name()).isEqualTo("研发知识库");
        assertThat(knowledgeBase.ownerId()).isEqualTo("user-1");
        assertThat(knowledgeBase.department()).isEqualTo("研发部");
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void getRequiredShouldThrowWhenKnowledgeBaseNotFound() {
        assertThatThrownBy(() -> service.getRequired(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识库不存在");
    }

    private KnowledgeBase createResearchKnowledgeBase() {
        return service.create(new CreateKnowledgeBaseRequest(
                "研发知识库",
                "保存研发制度和项目文档",
                "user-1",
                "研发部"
        ));
    }
    @Test
    void getRequiredWithAccessShouldAllowOwner() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();
        assertThat(service.getRequiredWithAccess(knowledgeBase.id(), "user-1", "销售部")).isEqualTo(knowledgeBase);
    }

    @Test
    void getRequiredWithAccessShouldAllowSameDepartmentUser() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();
        assertThat(service.getRequiredWithAccess(knowledgeBase.id(), "user-2", "研发部")).isEqualTo(knowledgeBase);
    }

    @Test
    void getRequiredWithAccessShouldRejectUnauthorizedUser() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();
        assertThatThrownBy(() -> service.getRequiredWithAccess(knowledgeBase.id(), "user-2", "测试部"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权访问");
    }
}

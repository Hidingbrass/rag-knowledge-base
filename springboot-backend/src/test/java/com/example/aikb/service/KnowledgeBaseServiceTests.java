package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.dto.knowledgebase.UpdateKnowledgeBaseRequest;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.entity.ToolAction;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.enums.MessageRole;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import com.example.aikb.repository.ChatMessageRepository;
import com.example.aikb.repository.ChatSessionRepository;
import com.example.aikb.repository.KnowledgeDocumentRepository;
import com.example.aikb.repository.KnowledgeBaseRepository;
import com.example.aikb.repository.ToolActionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ToolActionRepository toolActionRepository;

    @MockBean
    private FastApiRagClient fastApiRagClient;

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
    void createShouldTrimOwnerIdAndDepartment() {
        KnowledgeBase knowledgeBase = service.create(new CreateKnowledgeBaseRequest(
                "研发知识库",
                "保存研发制度和项目文档",
                " user-1 ",
                " 研发部 "
        ));

        assertThat(knowledgeBase.ownerId()).isEqualTo("user-1");
        assertThat(knowledgeBase.department()).isEqualTo("研发部");
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
    void getRequiredWithAccessShouldRejectSameDepartmentNonOwner() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();
        assertThatThrownBy(() -> service.getRequiredWithAccess(knowledgeBase.id(), "user-2", "研发部"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void getRequiredWithAccessShouldTrimIdentityBeforePermissionCheck() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();

        assertThat(service.getRequiredWithAccess(knowledgeBase.id(), " user-1 ", " 销售部 ")).isEqualTo(knowledgeBase);
    }

    @Test
    void getRequiredWithAccessShouldRejectBlankIdentity() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();

        assertThatThrownBy(() -> service.getRequiredWithAccess(knowledgeBase.id(), " ", "研发部"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户 ID 不能为空");

        assertThatThrownBy(() -> service.getRequiredWithAccess(knowledgeBase.id(), "user-1", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门不能为空");
    }

    @Test
    void getRequiredWithAccessShouldRejectUnauthorizedUser() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();
        assertThatThrownBy(() -> service.getRequiredWithAccess(knowledgeBase.id(), "user-2", "测试部"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void listAccessibleShouldOnlyReturnOwnedKnowledgeBases() {
        KnowledgeBase owned = service.create(new CreateKnowledgeBaseRequest(
                "我的销售知识库",
                "owner 访问",
                "user-1",
                "销售部"
        ));
        KnowledgeBase sameLearningDirection = service.create(new CreateKnowledgeBaseRequest(
                "他人的研发知识库",
                "学习方向相同但不可访问",
                "user-2",
                "研发部"
        ));
        service.create(new CreateKnowledgeBaseRequest(
                "财务私有知识库",
                "不可访问",
                "user-3",
                "财务部"
        ));

        assertThat(service.listAccessible("user-1", "研发部"))
                .extracting(KnowledgeBase::id)
                .containsExactly(owned.id())
                .doesNotContain(sameLearningDirection.id());
    }

    @Test
    void updateShouldChangeEditableFieldsAndPreserveOwnership() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();

        KnowledgeBase updated = service.update(
                knowledgeBase.id(),
                "user-1",
                "研发部",
                new UpdateKnowledgeBaseRequest(" Java 面试库 ", " 冲刺面试 ", " 后端开发 ")
        );

        assertThat(updated.name()).isEqualTo("Java 面试库");
        assertThat(updated.description()).isEqualTo("冲刺面试");
        assertThat(updated.department()).isEqualTo("后端开发");
        assertThat(updated.ownerId()).isEqualTo("user-1");
    }

    @Test
    void updateAndDeleteShouldRejectNonOwner() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();

        assertThatThrownBy(() -> service.update(
                knowledgeBase.id(),
                "user-2",
                "研发部",
                new UpdateKnowledgeBaseRequest("越权修改", "", "研发部")
        )).isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> service.delete(knowledgeBase.id(), "user-2", "研发部"))
                .isInstanceOf(ForbiddenException.class);
        verify(fastApiRagClient, never()).deleteDocument("vector-doc-1");
    }

    @Test
    void deleteShouldRemoveVectorsDocumentsSessionsAndMessages() {
        KnowledgeBase knowledgeBase = createResearchKnowledgeBase();
        Instant now = Instant.now();
        KnowledgeDocument document = documentRepository.save(new KnowledgeDocument(
                UUID.randomUUID(), knowledgeBase.id(), "vector-doc-1", "notes.md", "hash-1",
                DocumentStatus.AVAILABLE, 3, null, now, now
        ));
        ChatSession session = chatSessionRepository.save(new ChatSession(
                UUID.randomUUID(), knowledgeBase.id(), "user-1", "复习会话", now
        ));
        chatMessageRepository.save(new ChatMessage(
                UUID.randomUUID(), session.id(), MessageRole.USER, "什么是 RAG？", null,
                null, null, now
        ));
        UUID assistantMessageId = UUID.randomUUID();
        chatMessageRepository.save(new ChatMessage(
                assistantMessageId, session.id(), MessageRole.ASSISTANT, "待确认", null,
                "tool_confirmation_required", null, now
        ));
        ToolAction action = toolActionRepository.save(new ToolAction(
                UUID.randomUUID(), "user-1", "研发部", session.id(), assistantMessageId,
                "delete_knowledge_document", "{}", now.plusSeconds(300), now
        ));

        service.delete(knowledgeBase.id(), "user-1", "研发部");

        verify(fastApiRagClient).deleteDocument("vector-doc-1");
        assertThat(knowledgeBaseRepository.findById(knowledgeBase.id())).isEmpty();
        assertThat(documentRepository.findById(document.id())).isEmpty();
        assertThat(chatSessionRepository.findById(session.id())).isEmpty();
        assertThat(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id())).isEmpty();
        assertThat(toolActionRepository.findById(action.id())).isEmpty();
    }
}

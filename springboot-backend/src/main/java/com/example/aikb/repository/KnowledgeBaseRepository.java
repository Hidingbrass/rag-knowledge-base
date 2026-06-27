package com.example.aikb.repository;

import com.example.aikb.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 知识库 JPA Repository。
 *
 * JpaRepository<KnowledgeBase, UUID> 的含义：
 * - KnowledgeBase：这个 Repository 操作哪张实体表。
 * - UUID：这张表主键 id 的类型。
 *
 * findAllByOrderByCreatedAtDesc 不是随便起名。
 * Spring Data JPA 会解析方法名，自动生成“按创建时间倒序查询全部知识库”的 SQL。
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
    List<KnowledgeBase> findAllByOrderByCreatedAtDesc();
}

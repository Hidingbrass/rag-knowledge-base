package com.example.aikb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识库数据库实体。
 *
 * Entity 表示“Java 对象”和“MySQL 表”的映射关系。
 * 这张表保存知识库本身的业务信息，例如名称、创建人、所属部门。
 *
 * 注意：这里保留 id()、name() 这类方法，是为了兼容项目里已经写好的 DTO 和 Service。
 * JPA 本身通常使用 getId() 这种 getter，但这里先减少迁移成本。
 */
@Entity
@Table(name = "knowledge_base")
public class KnowledgeBase {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private Instant createdAt;

    protected KnowledgeBase() {
        // JPA 需要无参构造方法，用来从数据库查询结果创建对象。
    }

    public KnowledgeBase(
            UUID id,
            String name,
            String description,
            String ownerId,
            String department,
            Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.department = department;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String ownerId() {
        return ownerId;
    }

    public String department() {
        return department;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

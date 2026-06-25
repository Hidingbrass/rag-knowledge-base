package com.example.aikb.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识库业务模型。
 *
 * 现在先用普通 Java record 表达业务数据，后面接 MySQL 时可以改成 JPA Entity。
 *
 * 字段说明：
 * - id：Spring Boot 业务系统里的知识库 ID。
 * - name：知识库名称，例如“研发制度知识库”。
 * - description：知识库描述。
 * - ownerId：创建者 ID，后续做权限时会用到。
 * - department：所属部门，后续做部门隔离时会用到。
 * - createdAt：创建时间。
 */
public record KnowledgeBase(
        UUID id,
        String name,
        String description,
        String ownerId,
        String department,
        Instant createdAt
) {
}

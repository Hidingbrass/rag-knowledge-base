package com.example.aikb.service;

import com.example.aikb.entity.KnowledgeDocument;

/**
 * 文档入库业务结果。
 *
 * KnowledgeDocument 表示数据库里的文档记录；
 * duplicated 表示“本次上传请求是否命中了重复文档”。
 *
 * 这个字段不放进 Entity，因为 duplicated 不是数据库里的长期状态，
 * 它只描述“这一次接口调用”的结果。
 */
public record DocumentIndexResult(
        KnowledgeDocument document,
        boolean duplicated
) {
}

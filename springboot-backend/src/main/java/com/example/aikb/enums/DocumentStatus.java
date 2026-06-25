package com.example.aikb.enums;

/**
 * 文档在 Spring Boot 业务系统中的状态。
 *
 * 注意：这里保存的是“业务状态”，不是 Qdrant 里的向量状态。
 * Spring Boot 需要知道一份文档当前能不能被用户查询，所以必须有状态字段。
 */
public enum DocumentStatus {

    /**
     * 已接收到上传请求，但还没有开始调用 FastAPI 入库。
     */
    UPLOADED,

    /**
     * 正在调用 FastAPI 进行 PDF 解析、切分、Embedding 和 Qdrant 入库。
     */
    PROCESSING,

    /**
     * FastAPI 入库成功，文档已经可以用于 RAG 问答。
     */
    AVAILABLE,

    /**
     * 入库失败，例如 PDF 不合法、FastAPI 不可用、模型服务失败等。
     */
    FAILED
}

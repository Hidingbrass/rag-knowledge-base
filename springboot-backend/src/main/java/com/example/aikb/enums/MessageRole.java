package com.example.aikb.enums;

/**
 * 聊天消息角色。
 *
 * 一条聊天记录里，用户问题和 AI 回答都属于消息。
 * role 用来区分这条消息是谁说的。
 */
public enum MessageRole {

    /**
     * 用户消息。
     */
    USER,

    /**
     * AI 助手消息。
     */
    ASSISTANT
}

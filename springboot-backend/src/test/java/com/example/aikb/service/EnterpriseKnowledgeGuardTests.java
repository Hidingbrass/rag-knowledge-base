package com.example.aikb.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseKnowledgeGuardTests {

    private final EnterpriseKnowledgeGuard guard = new EnterpriseKnowledgeGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "请总结这份文档",
            "资料里提到的超时配置是多少？",
            "我们公司的报销制度是什么？",
            "公司合同中的付款期限是多少？",
            "本项目的客户权限如何配置？",
            "当前系统的 JWT 过期时间是多少？",
            "What does this document say about retries?",
            "Explain the internal policy"
    })
    void shouldProtectStrongEnterpriseKnowledgeSignals(String question) {
        assertThat(guard.requiresRag(question)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1+1 等于多少？",
            "写一首关于春天的短诗",
            "帮我查一下明天的天气",
            "Spring 事务是什么？"
    })
    void shouldLeaveNonEnterpriseQueriesToClassifier(String question) {
        assertThat(guard.requiresRag(question)).isFalse();
    }
}

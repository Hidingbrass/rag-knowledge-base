package com.example.aikb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SmallTalkRouterTests {

    private final SmallTalkRouter router = new SmallTalkRouter();

    @ParameterizedTest
    @ValueSource(strings = {"你好", " 你好！ ", "您好呀。", "Hello!!!", "Ｈｉ！", "早上好"})
    void shouldRoutePureGreetings(String question) {
        SmallTalkRouter.SmallTalkReply reply = router.route(question).orElseThrow();

        assertThat(reply.intent()).isEqualTo(SmallTalkRouter.Intent.GREETING);
        assertThat(reply.answer()).contains("知途 AI");
    }

    @ParameterizedTest
    @ValueSource(strings = {"谢谢", "谢谢你！", "明白了谢谢。", "THANKS!", "Thank you～", "辛苦了"})
    void shouldRoutePureThanks(String question) {
        SmallTalkRouter.SmallTalkReply reply = router.route(question).orElseThrow();

        assertThat(reply.intent()).isEqualTo(SmallTalkRouter.Intent.THANKS);
        assertThat(reply.answer()).contains("不客气");
    }

    @ParameterizedTest
    @ValueSource(strings = {"你是谁？", "你能做什么", "有什么功能！", "如何使用这个助手？", "HELP"})
    void shouldRouteCapabilityQuestions(String question) {
        SmallTalkRouter.SmallTalkReply reply = router.route(question).orElseThrow();

        assertThat(reply.intent()).isEqualTo(SmallTalkRouter.Intent.CAPABILITY);
        assertThat(reply.answer()).contains("资料").contains("来源");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "你好，请总结这份文档",
            "你好！请总结这份文档",
            "你好像没有回答我的问题",
            "文档里为什么说你好？",
            "谢谢，请继续解释 Spring 事务",
            "hi, summarize this document",
            "RAG 是什么？"
    })
    void shouldNotRouteMixedOrKnowledgeQuestions(String question) {
        assertThat(router.route(question)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "！！！"})
    void shouldIgnoreMissingOrPunctuationOnlyInput(String question) {
        assertThat(router.route(question)).isEmpty();
    }

    @Test
    void shouldPreserveInternalPunctuationWhenMatching() {
        assertThat(router.route("你，能做什么？")).isEmpty();
    }
}

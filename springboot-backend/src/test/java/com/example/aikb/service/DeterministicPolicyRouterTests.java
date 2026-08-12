package com.example.aikb.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicPolicyRouterTests {

    private final DeterministicPolicyRouter router = new DeterministicPolicyRouter(
            new EnterpriseKnowledgeGuard()
    );

    @ParameterizedTest
    @ValueSource(strings = {
            "请把这个账号注销",
            "立即发送这封邮件",
            "Delete this document now"
    })
    void shouldBlockDirectWriteActionsBeforeAnyModelCall(String question) {
        var decision = router.route(question).orElseThrow();

        assertThat(decision.route()).isEqualTo(DeterministicPolicyRouter.PolicyRoute.DIRECT);
        assertThat(decision.auditMode()).isEqualTo("destructive_action_blocked");
        assertThat(decision.reasonCode()).isEqualTo("write_action_guard");
        assertThat(decision.answer()).contains("不会执行");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "帮我删除这份知识库文档。",
            "把当前选中文档移除"
    })
    void shouldRouteOnlyCurrentDocumentDeletionToConfirmation(String question) {
        var decision = router.route(question).orElseThrow();

        assertThat(decision.route()).isEqualTo(DeterministicPolicyRouter.PolicyRoute.WRITE_TOOL);
        assertThat(decision.auditMode()).isEqualTo("tool_confirmation_required");
        assertThat(decision.toolName()).isEqualTo("delete_knowledge_document");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "今天合肥天气怎么样？"
    })
    void shouldRouteCurrentWeatherToAuthorizedReadTool(String question) {
        var decision = router.route(question).orElseThrow();

        assertThat(decision.route()).isEqualTo(DeterministicPolicyRouter.PolicyRoute.READ_TOOL);
        assertThat(decision.toolName()).isEqualTo("get_current_weather");
    }

    @ParameterizedTest
    @ValueSource(strings = {"现在美元汇率是多少？", "查询明天上海到北京的航班状态。"})
    void shouldKeepUnsupportedRealtimeQueriesUnavailable(String question) {
        var decision = router.route(question).orElseThrow();
        assertThat(decision.route()).isEqualTo(DeterministicPolicyRouter.PolicyRoute.DIRECT);
        assertThat(decision.auditMode()).isEqualTo("realtime_tool_unavailable");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "公司合同中的付款期限是多少？",
            "这份文档中的天气案例说明了什么？",
            "当前系统的 JWT 过期时间是多少？"
    })
    void shouldKeepEnterpriseOrInformationalDocumentQuestionsOnRag(String question) {
        var decision = router.route(question).orElseThrow();

        assertThat(decision.route()).isEqualTo(DeterministicPolicyRouter.PolicyRoute.RAG);
        assertThat(decision.reasonCode()).isEqualTo("enterprise_rule");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1+1 等于多少？",
            "写一首关于春天的诗",
            "JWT 是什么？",
            "请说明如何删除知识库文档"
    })
    void shouldLeaveOtherQueriesForClassifier(String question) {
        assertThat(router.route(question)).isEmpty();
    }
}

package com.example.aikb.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 在概率分类器之前处理不能依赖模型决定的路由策略。
 *
 * <p>这里仅放高精度、失败后果明确的规则：外部写操作必须被阻断，明确企业知识必须
 * 进入 RAG，实时外部信息在没有已授权工具时必须透明说明不可用。其他问题仍交给分类器。</p>
 */
@Component
public class DeterministicPolicyRouter {

    private static final String ACTION_VERBS =
            "删除|移除|清空|销毁|注销|发送|发布|创建|新建|修改|更新|提交|支付|转账";
    private static final String ACTION_TARGETS =
            "文档|文件|知识库|资料|记录|数据|账号|用户|消息|邮件|任务|项目|合同|订单|付款";

    private static final Pattern INFORMATIONAL_ACTION = Pattern.compile(
            "(?:(?:如何|怎么|怎样|为什么|能否|是否|步骤|流程|方法|教程|说明|解释|介绍)"
                    + ".{0,20}(?:" + ACTION_VERBS + ")"
                    + "|(?:" + ACTION_VERBS + ").{0,20}(?:步骤|流程|方法|教程|规则|权限|说明))"
    );
    private static final Pattern DIRECT_ACTION = Pattern.compile(
            "(?:(?:请|请你|帮我|麻烦|替我|给我|现在|立即|马上)?(?:把)?"
                    + "[^。！？?]{0,16}(?:" + ACTION_VERBS + ")"
                    + "[^。！？?]{0,24}(?:" + ACTION_TARGETS + ")"
                    + "|(?:请|请你|帮我|麻烦|替我|给我|现在|立即|马上)?(?:把)?"
                    + "[^。！？?]{0,8}(?:" + ACTION_TARGETS + ")"
                    + "[^。！？?]{0,12}(?:" + ACTION_VERBS + "))"
    );
    private static final Pattern ENGLISH_DIRECT_ACTION = Pattern.compile(
            "\\b(?:delete|remove|clear|destroy|disable|send|publish|create|update|modify|submit|pay)\\b"
                    + ".{0,32}\\b(?:document|file|knowledge\\s+base|record|data|account|message|task|contract|order)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ENGLISH_INFORMATIONAL_ACTION = Pattern.compile(
            "\\b(?:how|why|whether|steps?|guide|explain)\\b.{0,32}"
                    + "\\b(?:delete|remove|clear|destroy|send|create|update|modify)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REALTIME_QUERY = Pattern.compile(
            "(?:(?:天气|气温|降雨|空气质量).{0,16}(?:怎么样|如何|多少|几度|会不会|有雨|查询|查|预报)"
                    + "|(?:今天|明天|后天|现在|当前|实时|最新).{0,16}"
                    + "(?:天气|气温|降雨|空气质量|汇率|股价|股票价格|航班|列车|快递|物流|新闻|热搜|路况)"
                    + "|(?:天气|气温|降雨|空气质量|汇率|股价|股票价格|航班|列车|快递|物流|新闻|热搜|路况)"
                    + ".{0,16}(?:今天|明天|后天|现在|当前|实时|最新))"
    );
    private static final Pattern ENGLISH_REALTIME_QUERY = Pattern.compile(
            "\\b(?:today|tomorrow|current|currently|live|latest)\\b.{0,32}"
                    + "\\b(?:weather|temperature|exchange\\s+rate|stock\\s+price|flight|news|traffic)\\b"
                    + "|\\b(?:weather|temperature|exchange\\s+rate|stock\\s+price|flight|news|traffic)\\b"
                    + ".{0,32}\\b(?:today|tomorrow|current|currently|live|latest)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final String WRITE_ACTION_BLOCKED_ANSWER =
            "当前聊天接口没有已授权的写操作工具，因此不会执行删除、修改、发送等操作。"
                    + "请到对应管理入口核对目标和权限，并在明确确认后操作。";
    private static final String REALTIME_TOOL_UNAVAILABLE_ANSWER =
            "当前没有接入可验证的实时数据工具，不能可靠回答这类实时查询。"
                    + "请使用已授权的数据源，或稍后在工具接入后重试。";

    private final EnterpriseKnowledgeGuard enterpriseKnowledgeGuard;

    public DeterministicPolicyRouter(EnterpriseKnowledgeGuard enterpriseKnowledgeGuard) {
        this.enterpriseKnowledgeGuard = enterpriseKnowledgeGuard;
    }

    public Optional<PolicyDecision> route(String question) {
        String normalized = normalize(question);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        if (isDirectWriteAction(normalized)) {
            return Optional.of(new PolicyDecision(
                    PolicyRoute.DIRECT,
                    WRITE_ACTION_BLOCKED_ANSWER,
                    "destructive_action_blocked",
                    "write_action_guard"
            ));
        }

        if (enterpriseKnowledgeGuard.requiresRag(normalized)) {
            return Optional.of(new PolicyDecision(
                    PolicyRoute.RAG,
                    null,
                    "rag",
                    "enterprise_rule"
            ));
        }

        if (REALTIME_QUERY.matcher(normalized).find()
                || ENGLISH_REALTIME_QUERY.matcher(normalized).find()) {
            return Optional.of(new PolicyDecision(
                    PolicyRoute.DIRECT,
                    REALTIME_TOOL_UNAVAILABLE_ANSWER,
                    "realtime_tool_unavailable",
                    "realtime_rule"
            ));
        }

        return Optional.empty();
    }

    PolicyDecision blockWriteAction(String reasonCode) {
        return new PolicyDecision(
                PolicyRoute.DIRECT,
                WRITE_ACTION_BLOCKED_ANSWER,
                "destructive_action_blocked",
                reasonCode
        );
    }

    PolicyDecision realtimeToolUnavailable(String reasonCode) {
        return new PolicyDecision(
                PolicyRoute.DIRECT,
                REALTIME_TOOL_UNAVAILABLE_ANSWER,
                "realtime_tool_unavailable",
                reasonCode
        );
    }

    private boolean isDirectWriteAction(String normalized) {
        if (INFORMATIONAL_ACTION.matcher(normalized).find()
                || ENGLISH_INFORMATIONAL_ACTION.matcher(normalized).find()) {
            return false;
        }
        return DIRECT_ACTION.matcher(normalized).find()
                || ENGLISH_DIRECT_ACTION.matcher(normalized).find();
    }

    private String normalize(String question) {
        if (question == null) {
            return "";
        }
        return Normalizer.normalize(question, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    public enum PolicyRoute {
        RAG,
        DIRECT
    }

    public record PolicyDecision(
            PolicyRoute route,
            String answer,
            String auditMode,
            String reasonCode
    ) {
    }
}

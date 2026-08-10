package com.example.aikb.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 对明确指向当前资料或企业内部事实的消息执行高召回保护。
 *
 * <p>误命中只会让问题继续进入 RAG；漏命中仍有后置分类器的
 * enterpriseKnowledge 标记和置信度阈值兜底。</p>
 */
@Component
public class EnterpriseKnowledgeGuard {

    private static final List<Pattern> STRONG_SIGNALS = List.of(
            Pattern.compile("(?:这|该|上述|当前|选中)(?:一|这)?(?:份|个)?(?:文档|资料|文件|章节|知识库)"),
            Pattern.compile("(?:文档|资料|文件|知识库)(?:中|里|内|提到|写到|显示|说明)"),
            Pattern.compile("(?:根据|基于|按照)(?:这|该|上述|当前|选中)?(?:一|这)?(?:份|个)?(?:文档|资料|文件|知识库)"),
            Pattern.compile("(?:我们公司|本公司|我司|公司内部|企业内部|团队内部|部门内部).{0,12}(?:制度|政策|流程|规定|数据|配置|项目|客户|合同|权限|业务)"),
            Pattern.compile("(?:本项目|当前项目|团队项目).{0,12}(?:制度|流程|数据|配置|客户|合同|权限|业务)"),
            Pattern.compile("(?:公司|企业|部门|团队|项目|系统|平台)(?:的|内部|当前|现有|所用)?.{0,12}(?:合同|制度|政策|流程|配置|权限|预算|客户|订单|付款期限|jwt|token|令牌)"),
            Pattern.compile("(?:internal|company|enterprise|team)\\s+(?:policy|process|data|configuration|project|customer|contract)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:this|selected|current)\\s+(?:document|file|knowledge\\s+base)", Pattern.CASE_INSENSITIVE)
    );

    public boolean requiresRag(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(question, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT);
        return STRONG_SIGNALS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }
}

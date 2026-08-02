package com.example.aikb.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 对无需知识库事实依据的有限小聊执行确定性路由。
 *
 * <p>这里只接受规范化后的整句白名单，不做包含或前缀匹配。这样
 * “你好，请总结这份资料”仍会进入 RAG，不会因为问候前缀绕过检索与限流。</p>
 */
@Component
public class SmallTalkRouter {

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\p{P}\\p{S}\\s]+$");

    private static final Set<String> GREETINGS = Set.of(
            "你好",
            "您好",
            "您好呀",
            "您好啊",
            "你好呀",
            "你好啊",
            "嗨",
            "哈喽",
            "hello",
            "hi",
            "早上好",
            "上午好",
            "中午好",
            "下午好",
            "晚上好"
    );

    private static final Set<String> THANKS = Set.of(
            "谢谢",
            "谢谢你",
            "感谢",
            "感谢你",
            "多谢",
            "辛苦了",
            "好的谢谢",
            "明白了谢谢",
            "收到谢谢",
            "thanks",
            "thank you"
    );

    private static final Set<String> CAPABILITIES = Set.of(
            "你是谁",
            "你能做什么",
            "你会做什么",
            "你可以做什么",
            "你能帮我做什么",
            "你可以帮我做什么",
            "你有什么功能",
            "有什么功能",
            "功能介绍",
            "如何使用",
            "怎么使用",
            "怎么使用这个助手",
            "如何使用这个助手",
            "help"
    );

    private static final String GREETING_ANSWER =
            "你好！我是知途 AI 伴学助手，可以基于你选择的学习资料回答问题并标注来源。";
    private static final String THANKS_ANSWER =
            "不客气！你可以继续追问当前资料中的知识点。";
    private static final String CAPABILITY_ANSWER =
            "我可以基于当前选择的资料总结知识框架、解释和对比概念、生成练习题，并为知识回答标注来源；资料不足时我会明确说明。";

    public Optional<SmallTalkReply> route(String question) {
        String normalized = normalize(question);
        if (GREETINGS.contains(normalized)) {
            return Optional.of(new SmallTalkReply(Intent.GREETING, GREETING_ANSWER));
        }
        if (THANKS.contains(normalized)) {
            return Optional.of(new SmallTalkReply(Intent.THANKS, THANKS_ANSWER));
        }
        if (CAPABILITIES.contains(normalized)) {
            return Optional.of(new SmallTalkReply(Intent.CAPABILITY, CAPABILITY_ANSWER));
        }
        return Optional.empty();
    }

    private String normalize(String question) {
        if (question == null) {
            return "";
        }
        String normalized = Normalizer.normalize(question, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT);
        return TRAILING_PUNCTUATION.matcher(normalized).replaceAll("").strip();
    }

    public enum Intent {
        GREETING,
        THANKS,
        CAPABILITY
    }

    public record SmallTalkReply(Intent intent, String answer) {
    }
}

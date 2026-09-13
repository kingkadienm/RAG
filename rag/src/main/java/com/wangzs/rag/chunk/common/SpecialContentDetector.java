package com.wangzs.rag.chunk.common;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.chunk.model.DocumentElement;
import com.wangzs.rag.chunk.model.ElementType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 特殊内容检测器
 * <p>
 * 检测文档中的特殊内容类型：
 * <pre>
 * FAQ（问答对）
 * 表格（TABLE）
 * 代码块（CODE_BLOCK）
 * JSON 对象/数组
 * 列表（LIST）
 * </pre>
 */
public class SpecialContentDetector {

    // ---- FAQ 检测 ----

    private static final Pattern FAQ_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*[Qq][：:?]\\s*(.+?)(?:\\n\\s*[Aa][：:]?\\s*(.+?))+(?:\\n|$)",
            Pattern.DOTALL
    );

    private static final Pattern FAQ_QUESTION_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*[Qq][：:?]\\s*(.+)",
            Pattern.MULTILINE
    );

    private static final Pattern FAQ_ANSWER_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*[Aa][：:]?\\s*(.+)",
            Pattern.MULTILINE
    );

    // ---- JSON 检测 ----

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile(
            "\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}"
    );

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile(
            "\\[[^\\[\\]]*(?:\\[[^\\[\\]]*\\][^\\[\\]]*)*\\]"
    );

    // ---- 代码块检测 ----

    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile(
            "```([\\w]*)\\n?([\\s\\S]*?)```"
    );

    /**
     * 拆分文本中的代码块，返回 (代码块前的文本, 代码块内容, 代码块后的文本)
     */
    public static List<String> extractCodeBlocks(String text) {
        List<String> parts = new ArrayList<>();
        java.util.regex.Matcher matcher = CODE_FENCE_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                parts.add(text.substring(lastEnd, matcher.start()));
            }
            parts.add(matcher.group(0));
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            parts.add(text.substring(lastEnd));
        }

        return parts;
    }

    // ---- 列表检测 ----

    private static final Pattern LIST_PATTERN = Pattern.compile(
            "^(?:\\s*[-*+]\\s+|\\s*\\d+\\.\\s+)",
            Pattern.MULTILINE
    );

    /**
     * 检测文本是否包含 FAQ
     */
    public static boolean isFAQ(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return FAQ_PATTERN.matcher(text).find();
    }

    /**
     * 检测文本是否包含 JSON
     */
    public static boolean isJson(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // 快速检查：以 { 或 [ 开头
        String trimmed = text.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return false;
        }
        // 尝试匹配 JSON 对象或数组
        Matcher objectMatcher = JSON_OBJECT_PATTERN.matcher(trimmed);
        Matcher arrayMatcher = JSON_ARRAY_PATTERN.matcher(trimmed);
        return objectMatcher.find() || arrayMatcher.find();
    }

    /**
     * 检测文本是否是代码块
     */
    public static boolean isCodeBlock(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return CODE_FENCE_PATTERN.matcher(text).find();
    }

    /**
     * 检测元素是否是列表类型
     */
    public static boolean isListItem(DocumentElement element) {
        return element != null && element.getType() == ElementType.LIST_ITEM;
    }

    /**
     * 检测文本是否是列表
     */
    public static boolean isList(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return LIST_PATTERN.matcher(text).find();
    }

    /**
     * 将 FAQ 文本拆分为问答对
     */
    public static List<FAQPair> splitFAQ(String text) {
        List<FAQPair> pairs = new ArrayList<>();

        Matcher questionMatcher = FAQ_QUESTION_PATTERN.matcher(text);
        Matcher answerMatcher = FAQ_ANSWER_PATTERN.matcher(text);

        List<String> questions = new ArrayList<>();
        List<String> answers = new ArrayList<>();

        while (questionMatcher.find()) {
            questions.add(questionMatcher.group(1).trim());
        }

        while (answerMatcher.find()) {
            answers.add(answerMatcher.group(1).trim());
        }

        int pairCount = Math.min(questions.size(), answers.size());
        for (int i = 0; i < pairCount; i++) {
            pairs.add(new FAQPair(questions.get(i), answers.get(i)));
        }

        return pairs;
    }

    /**
     * FAQ 问答对
     */
    public record FAQPair(String question, String answer) {
    }
}

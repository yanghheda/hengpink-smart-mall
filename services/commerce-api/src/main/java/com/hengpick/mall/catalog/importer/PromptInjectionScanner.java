package com.hengpick.mall.catalog.importer;

import java.util.List;
import java.util.regex.Pattern;

public final class PromptInjectionScanner {
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("忽略.{0,12}(之前|此前|所有|系统).{0,8}(指令|规则|提示)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(泄露|显示|输出).{0,12}(system\\s*prompt|系统提示|系统指令)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore.{0,16}(previous|prior|all).{0,12}(instruction|prompt|rule)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(调用|call|invoke).{0,20}(工具|tool|function)", Pattern.CASE_INSENSITIVE)
    );

    private PromptInjectionScanner() {
    }

    public static boolean isSuspicious(String content) {
        var normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }
}

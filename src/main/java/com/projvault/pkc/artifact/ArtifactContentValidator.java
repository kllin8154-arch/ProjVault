package com.projvault.pkc.artifact;

import com.projvault.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ArtifactContentValidator {

    private static final Pattern CREATE_TABLE = Pattern.compile("(?i)\\bCREATE\\s+TABLE\\b");
    private static final Pattern PRIMARY_KEY = Pattern.compile("(?i)\\bPRIMARY\\s+KEY\\b");

    public void validate(ArtifactFormat format, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(422, "模型输出过短，未生成交付物");
        }
        if (format == ArtifactFormat.SQL) {
            validateSql(content);
        }
    }

    private void validateSql(String content) {
        String sql = stripOuterFence(content);
        if (sql.length() < 80 || !CREATE_TABLE.matcher(sql).find() || !PRIMARY_KEY.matcher(sql).find()) {
            throw new BusinessException(422, "SQL 草稿缺少建表语句或主键，未生成文件");
        }
        if (!sql.stripTrailing().endsWith(";") || !hasBalancedParentheses(sql)) {
            throw new BusinessException(422, "SQL 草稿疑似被截断或结构未闭合，未生成文件");
        }
    }

    private String stripOuterFence(String content) {
        return content.replaceFirst("(?s)^\\s*```(?:sql)?\\s*", "")
                .replaceFirst("(?s)\\s*```\\s*$", "")
                .strip();
    }

    private boolean hasBalancedParentheses(String sql) {
        int depth = 0;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (!singleQuote && !doubleQuote && !backtick) {
                if (current == '-' && next == '-') {
                    lineComment = true;
                    i++;
                    continue;
                }
                if (current == '/' && next == '*') {
                    blockComment = true;
                    i++;
                    continue;
                }
            }
            if (current == '\'' && !doubleQuote && !backtick && !isEscaped(sql, i)) {
                singleQuote = !singleQuote;
                continue;
            }
            if (current == '"' && !singleQuote && !backtick && !isEscaped(sql, i)) {
                doubleQuote = !doubleQuote;
                continue;
            }
            if (current == '`' && !singleQuote && !doubleQuote) {
                backtick = !backtick;
                continue;
            }
            if (singleQuote || doubleQuote || backtick) {
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth < 0) {
                return false;
            }
        }
        return depth == 0 && !singleQuote && !doubleQuote && !backtick && !blockComment;
    }

    private boolean isEscaped(String value, int index) {
        int slashes = 0;
        for (int i = index - 1; i >= 0 && value.charAt(i) == '\\'; i--) {
            slashes++;
        }
        return slashes % 2 == 1;
    }
}

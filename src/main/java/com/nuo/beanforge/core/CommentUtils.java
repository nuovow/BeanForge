package com.nuo.beanforge.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 注释归一化: 把 Javadoc / 行尾注释统一变成"干净文本行", 便于原样搬到新类里。
 *
 * <p>注释是这次转换里最容易被丢掉的东西, 所以规则要显式且可断言:
 * 去注释符号、剥每行的前导 {@code *}、去掉首尾空行、压缩连续空行,
 * 并且丢掉 {@code @author}/{@code @date}/{@code @since}/{@code @version} 这四个
 * 没有迁移价值的标签 (它们属于"谁写的、什么时候写的", 不属于 DTO 的字段语义)。</p>
 */
public final class CommentUtils {

    /**
     * 迁移到新类里没有意义的标签。
     */
    private static final List<String> DROPPED_TAGS = Arrays.asList(
        "@author", "@date", "@since", "@version", "@createDate", "@createTime", "@updateDate"
    );

    private CommentUtils() {
    }

    /**
     * 归一化 Javadoc 原文, 例如 {@code "/**\n * 用户账号\n *<p>登录用</p>\n *&#47;"} -> ["用户账号", "<p>登录用</p>"]。
     *
     * @param raw 含 {@code /**} 与 {@code *&#47;} 的原文; 传 null 返回空列表
     */
    public static List<String> fromJavadoc(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String body = raw.trim();
        if (body.startsWith("/**")) {
            body = body.substring(3);
        }
        if (body.endsWith("*/")) {
            body = body.substring(0, body.length() - 2);
        }
        return normalize(splitLines(body));
    }

    /**
     * 归一化行尾注释, 支持 {@code // xxx}、{@code /* xxx *&#47;} 以及跨行的块注释。
     */
    public static List<String> fromLineComment(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String body = raw.trim();
        if (body.startsWith("//")) {
            body = body.substring(2);
        } else if (body.startsWith("/*")) {
            body = body.substring(2);
            if (body.trim().endsWith("*/")) {
                body = body.substring(0, body.lastIndexOf("*/"));
            }
        }
        return normalize(splitLines(body));
    }

    /**
     * 判断一段注释原文是不是 Javadoc。
     */
    public static boolean isJavadoc(String raw) {
        return raw != null && raw.trim().startsWith("/**");
    }

    /**
     * 把注释文本行渲染成待写入源码的 Javadoc 块 (不含缩进, 调用方负责缩进)。
     *
     * <p>只有一行时压成 {@code /** xxx *&#47;}, 多行时展开。</p>
     */
    public static String renderJavadoc(List<String> comments, String indent) {
        if (comments == null || comments.isEmpty()) {
            return "";
        }
        List<String> safe = new ArrayList<>();
        for (String line : comments) {
            safe.add(escapeCommentEnd(line));
        }
        StringBuilder sb = new StringBuilder();
        if (safe.size() == 1) {
            sb.append(indent).append("/** ").append(safe.get(0)).append(" */");
            return sb.toString();
        }
        sb.append(indent).append("/**").append("\n");
        for (String line : safe) {
            if (line.isEmpty()) {
                sb.append(indent).append(" *").append("\n");
            } else {
                sb.append(indent).append(" * ").append(line).append("\n");
            }
        }
        sb.append(indent).append(" */");
        return sb.toString();
    }

    /**
     * 把单个词变成 Javadoc 行 (供类注释使用)。
     */
    public static List<String> ofLines(String text) {
        if (text == null) {
            return new ArrayList<>();
        }
        return normalize(splitLines(text));
    }

    /**
     * 注释里出现 {@code *&#47;} 会提前闭合注释, 这里把它拆开。
     */
    static String escapeCommentEnd(String line) {
        if (line.contains("*/")) {
            return line.replace("*/", "*&#47;");
        }
        return line;
    }

    private static String[] splitLines(String text) {
        return text.split("\r\n|\r|\n", -1);
    }

    private static List<String> normalize(String[] rawLines) {
        List<String> result = new ArrayList<>();
        boolean droppingTagBody = false;
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            // 剥掉每行开头的一到多个 '*'
            while (line.startsWith("*")) {
                line = line.substring(1).trim();
            }
            if (line.isEmpty()) {
                if (!result.isEmpty() && !result.get(result.size() - 1).isEmpty()) {
                    result.add("");
                }
                continue;
            }
            if (line.startsWith("@")) {
                droppingTagBody = startsWithAny(line, DROPPED_TAGS);
                if (droppingTagBody) {
                    continue;
                }
            } else if (droppingTagBody) {
                // 被丢掉的标签的续行
                continue;
            }
            result.add(line);
        }
        // 去掉尾部空行
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static boolean startsWithAny(String line, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (line.startsWith(prefix)) {
                // 标签名后必须是空白或结束, 避免 @authorName 这种被当成 @author
                int next = prefix.length();
                if (line.length() == next || Character.isWhitespace(line.charAt(next))) {
                    return true;
                }
            }
        }
        return false;
    }
}

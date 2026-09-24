package com.nuo.beanforge.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 字段类型目录 + 类型文本校验。
 *
 * <p>只做纯字符串/语法层面的事, 不碰 IDE API, 所以类型列的下拉候选、字母检索、
 * 合法性判定都能在 headless 环境里用断言覆盖 —— 类型是生成代码的语法原料,
 * 这里放过一个坏字符, 生成出来的 {@code .java} 就是编译不过的。</p>
 *
 * <p>校验用的是 <b>递归下降解析</b>而不是一堆正则: 类型是有嵌套结构的
 * ({@code Map<String, List<Long>>}), 正则很难给出"到底哪一层括号没闭合"这种有用的报错。</p>
 */
public final class TypeCatalog {

    /**
     * 类型列下拉的候选项, 按常用度排序 —— 前几项就是用户十有八九要用的。
     *
     * <p>顺序即默认展示顺序 (字母检索的同分项也按这个顺序稳定排列)。</p>
     */
    private static final List<String> SUGGESTIONS = Collections.unmodifiableList(Arrays.asList(
        "String",
        "Long",
        "Integer",
        "Boolean",
        "BigDecimal",
        "LocalDateTime",
        "LocalDate",
        "List<String>",
        "List<Long>",
        "List<Integer>",
        "Map<String, Object>",
        "Set<String>",
        "Double",
        "Date",
        "Short",
        "Byte",
        "Float",
        "BigInteger",
        "Character",
        "Object",
        "LocalTime",
        "Instant",
        "Timestamp",
        "UUID",
        "Collection<String>",
        "Optional<String>",
        "Map<String, String>",
        "Map<String, Long>",
        "Set<Long>",
        "List<Map<String, Object>>",
        "byte[]",
        "String[]",
        "Long[]",
        "Integer[]"
    ));

    /**
     * 泛型里 {@code ?} 之后可以出现的两个关键字。
     */
    private static final List<String> WILDCARD_BOUNDS = Arrays.asList("extends", "super");

    /**
     * 八个基本类型。它们和关键字一样不在标识符表里, 但做字段类型完全合法
     * (DTO 里一般不推荐, 不过拦下来是多余的 —— 手写 {@code int age} 也该放行)。
     */
    private static final List<String> PRIMITIVES = Arrays.asList(
        "boolean", "byte", "char", "short", "int", "long", "float", "double");

    private TypeCatalog() {
    }

    /**
     * 下拉候选 (只读)。
     */
    public static List<String> suggestions() {
        return SUGGESTIONS;
    }

    // ------------------------------------------------------------------ 字母检索

    /**
     * 前缀/完全命中有多少条时, 就不再往里补"只是碰巧含这几个字母"的弱匹配。
     *
     * <p>没有这条规则, 敲一个 {@code b} 会带出 {@code Double}、{@code Object}
     * (它们都含字母 b) —— 匹配算法没错, 但对挑类型的人是纯噪音。</p>
     */
    private static final int STRONG_MATCH_MIN = 3;

    /**
     * 按用户敲进去的字母检索类型, 前缀命中排在最前。
     *
     * <p>分级 (同级保持 {@link #SUGGESTIONS} 的原顺序):</p>
     * <ol>
     *   <li>完全相同</li>
     *   <li>从头匹配 —— 敲 {@code Str} 出 {@code String}</li>
     *   <li>词首匹配 —— 敲 {@code date} 能出 {@code LocalDateTime}; 泛型参数也算词首,
     *       敲 {@code map} 出 {@code Map<String, Object>}</li>
     *   <li>任意位置包含 (仅在强匹配不足 {@value #STRONG_MATCH_MIN} 条时才补)</li>
     * </ol>
     *
     * @param keyword 用户输入 (大小写不敏感; 空/空白返回默认前 {@code limit} 条)
     * @param limit   最多返回多少条 (&lt;=0 表示不限)
     */
    public static List<String> filter(String keyword, int limit) {
        int max = limit <= 0 ? SUGGESTIONS.size() : limit;
        String key = keyword == null ? "" : keyword.trim();
        if (key.isEmpty()) {
            return new ArrayList<>(SUGGESTIONS.subList(0, Math.min(max, SUGGESTIONS.size())));
        }
        String lower = key.toLowerCase(Locale.ROOT);

        List<String> result = new ArrayList<>();
        int strongHits = 0;
        // 四级过滤: 同分项按候选表原顺序自然保持稳定
        for (int level = 0; level <= 3 && result.size() < max; level++) {
            boolean skipWeak = level == 3 && strongHits >= STRONG_MATCH_MIN;
            if (skipWeak) {
                break;
            }
            for (String candidate : SUGGESTIONS) {
                if (result.size() >= max) {
                    break;
                }
                if (score(candidate, lower) == level) {
                    result.add(candidate);
                    if (level <= 1) {
                        strongHits++;
                    }
                }
            }
        }
        return result;
    }

    /**
     * @return 0 完全相同 / 1 前缀 / 2 词首 / 3 包含 / -1 不匹配
     */
    static int score(String candidate, String lowerKeyword) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.equals(lowerKeyword)) {
            return 0;
        }
        if (lower.startsWith(lowerKeyword)) {
            return 1;
        }
        if (matchesAtWordStart(candidate, lowerKeyword)) {
            return 2;
        }
        return lower.contains(lowerKeyword) ? 3 : -1;
    }

    /**
     * 子串是否落在"词首": 前一个字符不是标识符字符, 或者是小写字母紧跟大写字母
     * (camelCase 的上升沿)。这样 {@code date} 能命中 {@code LocalDate} 的 {@code Date}。
     */
    static boolean matchesAtWordStart(String candidate, String lowerKeyword) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int index = lower.indexOf(lowerKeyword, from);
            if (index < 0) {
                return false;
            }
            if (index == 0) {
                return true;
            }
            char previous = candidate.charAt(index - 1);
            char current = candidate.charAt(index);
            boolean separator = !isIdentifierChar(previous);
            boolean camelEdge = Character.isLowerCase(previous) && Character.isUpperCase(current);
            if (separator || camelEdge) {
                return true;
            }
            from = index + 1;
        }
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    // ------------------------------------------------------------------ 规范化

    /**
     * 把输入整理成"写好格式的样子": 压掉多余空白, 逗号后统一一个空格。
     *
     * <p>刻意<b>不</b>做合法性判断 (非法文本原样返回, 只是去掉首尾空白) ——
     * 用户敲到一半 ({@code List<}) 时也要能显示出来, 否则编辑框里的字会被"吃掉"。</p>
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String result = text.replace('\u3000', ' ').trim();
        if (result.isEmpty()) {
            return "";
        }
        result = result.replaceAll("\\s+", " ");
        result = result.replaceAll("\\s*<\\s*", "<");
        result = result.replaceAll("\\s*>\\s*", ">");
        result = result.replaceAll("\\s*\\[\\s*\\]", "[]");
        result = result.replaceAll("\\s*,\\s*", ", ");
        return result.trim();
    }

    // ------------------------------------------------------------------ 校验

    /**
     * 校验一段类型文本是否是可用的 Java 类型引用。
     *
     * @return {@code null} 表示合法; 否则返回给用户看的中文错误说明
     */
    public static String validate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "类型不能为空";
        }
        String type = normalize(text);
        Cursor cursor = new Cursor(type);
        try {
            parseType(cursor);
            cursor.skipSpaces();
            if (!cursor.atEnd()) {
                throw new TypeSyntaxException("类型里有多余的内容: \"" + cursor.rest() + "\"");
            }
        } catch (TypeSyntaxException e) {
            return e.getMessage();
        }
        return null;
    }

    /**
     * @return 合法则返回规范化后的文本, 否则原样返回 (界面要能把用户敲的东西留在那儿)
     */
    public static String normalizeIfValid(String text) {
        return validate(text) == null ? normalize(text) : (text == null ? "" : text.trim());
    }

    private static void parseType(Cursor cursor) {
        cursor.skipSpaces();
        if (cursor.peekIs('?')) {
            cursor.next();
            cursor.skipSpaces();
            String word = cursor.peekWord();
            if (WILDCARD_BOUNDS.contains(word)) {
                cursor.next();
                cursor.advance(word.length());
                cursor.skipSpaces();
                parseQualifiedName(cursor);
            }
        } else {
            parseQualifiedName(cursor);
        }
        parseTypeArgs(cursor);
        // 数组维度写在最后: List<String>[] 也是合法类型
        while (true) {
            cursor.skipSpaces();
            if (cursor.peekIs('[')) {
                cursor.next();
                cursor.skipSpaces();
                if (!cursor.peekIs(']')) {
                    throw new TypeSyntaxException("数组的 \"[\" 没有写成 \"[]\"");
                }
                cursor.next();
            } else {
                break;
            }
        }
    }

    private static void parseQualifiedName(Cursor cursor) {
        parseIdentifier(cursor, "类型名");
        while (true) {
            cursor.skipSpaces();
            if (cursor.peekIs('.')) {
                cursor.next();
                cursor.skipSpaces();
                parseIdentifier(cursor, "包名里的类名");
            } else {
                break;
            }
        }
    }

    private static void parseIdentifier(Cursor cursor, String what) {
        char c = cursor.peek();
        if (c == 0) {
            throw new TypeSyntaxException("类型不完整, 缺少" + what);
        }
        if (!isAsciiJavaIdentifierStart(c)) {
            throw new TypeSyntaxException(
                what + "不能以 \"" + c + "\" 开头 (只能用字母、_ 或 $ 开头)");
        }
        cursor.markNameStart();
        cursor.next();
        while (!cursor.atEnd()) {
            char next = cursor.peek();
            if (!isAsciiJavaIdentifierPart(next)) {
                break;
            }
            cursor.next();
        }
        String name = cursor.consumedName();
        if (!DtoVoGenerator.isValidJavaIdentifier(name) && !PRIMITIVES.contains(name)) {
            throw new TypeSyntaxException("\"" + name + "\" 是 Java 关键字, 不能作为类型名");
        }
    }

    private static void parseTypeArgs(Cursor cursor) {
        cursor.skipSpaces();
        if (!cursor.peekIs('<')) {
            return;
        }
        cursor.next();
        int args = 0;
        while (true) {
            cursor.skipSpaces();
            if (cursor.peekIs('>')) {
                if (args == 0) {
                    throw new TypeSyntaxException("泛型 \"<>\" 里至少要写一个类型");
                }
                cursor.next();
                return;
            }
            if (cursor.atEnd()) {
                throw new TypeSyntaxException("泛型 \"<\" 没有对应的 \">\"");
            }
            parseType(cursor);
            args++;
            cursor.skipSpaces();
            if (cursor.peekIs(',')) {
                cursor.next();
                cursor.skipSpaces();
                // Map<String,> 这种"逗号后面没东西"要单独拦: 否则会被当成合法的单参数泛型
                if (cursor.atEnd() || cursor.peekIs('>')) {
                    throw new TypeSyntaxException("泛型里 \",\" 后面缺少类型");
                }
                continue;
            }
            if (cursor.peekIs('>')) {
                cursor.next();
                return;
            }
            // 参数读完就到底了 (Map<String, List<Long> 这种): 报"没闭合"而不是
            // 拿一个空字符去报"不能是 " 那种废话
            if (cursor.atEnd()) {
                throw new TypeSyntaxException("泛型 \"<\" 没有对应的 \">\"");
            }
            throw new TypeSyntaxException("泛型参数之间要用 \",\" 分隔, 不能是 \""
                + cursor.peek() + "\"");
        }
    }

    private static boolean isAsciiJavaIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    private static boolean isAsciiJavaIdentifierPart(char c) {
        return isAsciiJavaIdentifierStart(c) || (c >= '0' && c <= '9');
    }

    /**
     * 单遍字符游标。{@code consumedName()} 用来把刚吃掉的标识符捞出来做关键字检查。
     */
    private static final class Cursor {
        private final String text;
        private int index;
        private int nameStart;

        Cursor(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        char peek() {
            return atEnd() ? 0 : text.charAt(index);
        }

        boolean peekIs(char expected) {
            return peek() == expected;
        }

        void next() {
            index++;
        }

        void advance(int count) {
            index += count;
        }

        void skipSpaces() {
            while (!atEnd() && text.charAt(index) == ' ') {
                index++;
            }
        }

        /**
         * 当前位置往后看一个完整单词 (不含数字), 不清游标。
         */
        String peekWord() {
            int end = index;
            while (end < text.length() && Character.isLetter(text.charAt(end))) {
                end++;
            }
            return text.substring(index, end);
        }

        String consumedName() {
            return text.substring(nameStart, index);
        }

        /**
         * 记录标识符起点 —— 每次读标识符前调用。
         */
        void markNameStart() {
            nameStart = index;
        }

        String rest() {
            return text.substring(Math.min(index, text.length()));
        }
    }

    /**
     * 内部语法错误, 消息直接面向用户。
     */
    static class TypeSyntaxException extends RuntimeException {
        TypeSyntaxException(String message) {
            super(message);
        }
    }
}

package com.nuo.beanforge.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 把"用到哪些类型"翻译成 import 语句。
 *
 * <p>生成新文件时最容易出错的就是 import: 少一个编译不过, 多一个是黄线。
 * 这里只做三件事 —— 丢掉不用写的 (java.lang、同包、类自己)、按简单名去重、排序输出。</p>
 */
public final class ImportCollector {

    private ImportCollector() {
    }

    /**
     * @param qualifiedNames 用到的类型全限定名 (顺序不敏感, 会去重)
     * @param targetPackage  目标类所在包
     * @param targetClassName 目标类名 (自己不用 import 自己)
     */
    public static Result collect(Collection<String> qualifiedNames,
                                 String targetPackage,
                                 String targetClassName) {
        Set<String> statements = new TreeSet<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> simpleNameToQualified = new HashMap<>();

        String selfQualified = (targetPackage == null || targetPackage.isEmpty())
            ? targetClassName
            : targetPackage + "." + targetClassName;

        for (String qualified : new LinkedHashSet<>(qualifiedNames)) {
            if (qualified == null) {
                continue;
            }
            String trimmed = qualified.trim();
            if (trimmed.isEmpty() || trimmed.indexOf('.') < 0) {
                // 没有包名: 原始类型、类型变量 T、默认包里的类, 都不需要 import
                continue;
            }
            if (isSamePackage(trimmed, targetPackage)) {
                continue;
            }
            if (trimmed.equals(selfQualified)) {
                continue;
            }
            if (isJavaLang(trimmed)) {
                continue;
            }
            String simpleName = trimmed.substring(trimmed.lastIndexOf('.') + 1);
            String previous = simpleNameToQualified.putIfAbsent(simpleName, trimmed);
            if (previous != null && !previous.equals(trimmed)) {
                // 同名不同包: 保留先出现的那个, 另一个只能让用户自己改成全限定名
                warnings.add("类型名冲突: " + previous + " 与 " + trimmed
                    + " 都叫 " + simpleName + ", 已按前者生成 import, 后者请手改为全限定名");
                continue;
            }
            statements.add("import " + trimmed + ";");
        }

        return new Result(new ArrayList<>(statements), warnings);
    }

    private static boolean isSamePackage(String qualified, String targetPackage) {
        if (targetPackage == null || targetPackage.isEmpty()) {
            return false;
        }
        int lastDot = qualified.lastIndexOf('.');
        return lastDot > 0 && qualified.substring(0, lastDot).equals(targetPackage);
    }

    /**
     * java.lang 下的类型不用 import, 但 java.lang.annotation 要 (例如 @Retention)。
     */
    private static boolean isJavaLang(String qualified) {
        if (!qualified.startsWith("java.lang.")) {
            return false;
        }
        return !qualified.startsWith("java.lang.annotation.");
    }

    /**
     * import 收集结果。
     */
    public static class Result {
        private final List<String> statements;
        private final List<String> warnings;

        Result(List<String> statements, List<String> warnings) {
            this.statements = statements;
            this.warnings = warnings;
        }

        public List<String> getStatements() {
            return statements;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}

package com.nuo.beanforge.core;

import java.util.Arrays;
import java.util.List;

/**
 * 目标包名 / 类名推断。
 *
 * <p>{@code com.nuo.entity.UserEntity} + dto -> {@code com.nuo.dto.UserDTO}。
 * 推断只是给界面一个合理的初值, 用户随时可以改。</p>
 */
public final class Naming {

    /**
     * 实体包常见末段, 命中就把它换成 dto / vo, 否则在包名后面追加一段。
     */
    private static final List<String> ENTITY_PACKAGE_TAILS = Arrays.asList(
        "entity", "entities", "domain", "model", "po", "pojo", "dataobject", "bean", "beans", "do"
    );

    /**
     * 实体类常见后缀, 生成目标类名时先剥掉。
     *
     * <p>全大写和首字母大写两种写法都要认: 真实项目里 {@code UserVO} 和 {@code LoginVo} 都常见,
     * 少了哪一种都会让"从 LoginVo 反推实体"落空。</p>
     *
     * <p>刻意<b>不</b>做忽略大小写的匹配 —— 那样 {@code Todo} 会被 {@code DO} 误剥成 {@code To}。
     * 只列这两种形态就够覆盖真实命名, 又不会误伤普通单词。</p>
     */
    private static final List<String> ENTITY_CLASS_SUFFIXES = Arrays.asList(
        "Entity", "POJO", "Pojo", "DTO", "Dto", "QO", "Qo", "AO", "Ao",
        "BO", "Bo", "PO", "Po", "VO", "Vo", "DO", "Do"
    );

    private Naming() {
    }

    /**
     * {@code dto} -> {@code DTO}。
     */
    public static String upperKind(String kind) {
        return kind == null ? "" : kind.toUpperCase();
    }

    /**
     * 推断目标包名。
     */
    public static String guessTargetPackage(String entityPackage, String kind) {
        String suffix = kind == null ? "" : kind.toLowerCase();
        if (entityPackage == null || entityPackage.isEmpty()) {
            return suffix;
        }
        int lastDot = entityPackage.lastIndexOf('.');
        String tail = lastDot < 0 ? entityPackage : entityPackage.substring(lastDot + 1);
        if (ENTITY_PACKAGE_TAILS.contains(tail.toLowerCase())) {
            String head = lastDot < 0 ? "" : entityPackage.substring(0, lastDot + 1);
            return head + suffix;
        }
        return entityPackage + "." + suffix;
    }

    /**
     * 剥掉实体类名上的常见后缀: {@code UserEntity} -> {@code User}, {@code LoginVO} -> {@code Login}。
     *
     * <p>剥不动就原样返回 (例如 {@code User})。</p>
     */
    public static String stripKnownSuffix(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        for (String suffix : ENTITY_CLASS_SUFFIXES) {
            if (className.length() > suffix.length() && className.endsWith(suffix)) {
                return className.substring(0, className.length() - suffix.length());
            }
        }
        return className;
    }

    /**
     * 推断目标类名。
     *
     * <p>幂等: {@code UserDTO} 转 dto 仍然是 {@code UserDTO}, 不会变成 {@code UserDTODTO}。</p>
     */
    public static String guessClassName(String entityClassName, String kind) {
        String upper = upperKind(kind);
        if (entityClassName == null || entityClassName.isEmpty()) {
            return upper;
        }
        String base = stripKnownSuffix(entityClassName);
        if (base.isEmpty()) {
            base = entityClassName;
        }
        return base + upper;
    }
}

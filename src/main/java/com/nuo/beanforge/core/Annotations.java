package com.nuo.beanforge.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 注解的角色判定: 哪些是持久层的 (要去掉), 哪些是 Lombok 的 (要按选项重加)。
 *
 * <p>判定优先看全限定名, 拿不到全限定名 (注解类没进索引 / 不在 classpath) 时退回简单名名单。
 * 这两条路都必须保留: 老项目里 {@code @TableField} 是能解析的, 但从别的模块复制过来的
 * 自定义注解往往解析不了。</p>
 */
public final class Annotations {

    /**
     * 包名前缀命中即视为持久层注解。
     */
    private static final String[] PERSISTENCE_PREFIXES = {
        "com.baomidou.mybatisplus.annotation.",
        "com.baomidou.mybatisplus.extension.",
        "org.apache.ibatis.",
        "tk.mybatis.",
        "javax.persistence.",
        "jakarta.persistence.",
        "org.hibernate.annotations.",
        "org.hibernate.validator.internal.",
    };

    /**
     * 拿不到全限定名时的兜底简单名名单 (MyBatis / MyBatis-Plus / JPA 里最常见的那些)。
     */
    private static final Set<String> PERSISTENCE_SIMPLE_NAMES = new HashSet<>(Arrays.asList(
        // MyBatis-Plus
        "TableName", "TableId", "TableField", "TableLogic", "KeySequence", "EnumValue",
        "FieldFill", "InterceptorIgnore", "Version",
        // MyBatis
        "Mapper", "Param", "SelectKey", "Options", "Alias", "Flush",
        // JPA / jakarta
        "Entity", "Table", "Column", "Id", "GeneratedValue", "GenerationType",
        "SequenceGenerator", "TableGenerator", "JoinColumn", "JoinTable",
        "ManyToOne", "OneToMany", "OneToOne", "ManyToMany", "MapsId",
        "MappedSuperclass", "EmbeddedId", "Embedded", "Enumerated", "Temporal",
        "Lob", "Basic", "OrderBy", "OrderColumn", "PrimaryKeyJoinColumn",
        "Inheritance", "DiscriminatorColumn", "DiscriminatorValue", "Transient",
        "NamedQuery", "NamedQueries", "Access", "IdClass", "Converts",
        "Convert", "PostLoad", "PrePersist", "PreUpdate", "PreRemove",
        "PostPersist", "PostUpdate", "PostRemove", "Cacheable"
    ));

    private static final Set<String> LOMBOK_SIMPLE_NAMES = new HashSet<>(Arrays.asList(
        "Data", "Getter", "Setter", "ToString", "EqualsAndHashCode",
        "NoArgsConstructor", "RequiredArgsConstructor", "AllArgsConstructor",
        "Builder", "SuperBuilder", "Value", "Accessors", "FieldDefaults",
        "UtilityClass", "NonNull", "Delegate", "Jacksonized", "FieldNameConstants",
        "With", "Cleanup", "SneakyThrows", "Synchronized", "ExtensionMethod",
        "Log", "Slf4j", "Log4j", "Log4j2", "CommonsLog", "XSlf4j", "Flogger"
    ));

    /**
     * 类型文本 -> 常见 JDK 类型全限定名。用于用户手工添加字段时补 import
     * (手工字段没有 PSI 类型信息, 只能查表)。
     */
    private static final String[][] COMMON_TYPES = {
        {"BigDecimal", "java.math.BigDecimal"},
        {"BigInteger", "java.math.BigInteger"},
        {"LocalDate", "java.time.LocalDate"},
        {"LocalDateTime", "java.time.LocalDateTime"},
        {"LocalTime", "java.time.LocalTime"},
        {"Instant", "java.time.Instant"},
        {"Duration", "java.time.Duration"},
        {"Date", "java.util.Date"},
        {"Calendar", "java.util.Calendar"},
        {"Timestamp", "java.sql.Timestamp"},
        {"List", "java.util.List"},
        {"Set", "java.util.Set"},
        {"Map", "java.util.Map"},
        {"Collection", "java.util.Collection"},
        {"ArrayList", "java.util.ArrayList"},
        {"LinkedList", "java.util.LinkedList"},
        {"HashMap", "java.util.HashMap"},
        {"LinkedHashMap", "java.util.LinkedHashMap"},
        {"HashSet", "java.util.HashSet"},
        {"Optional", "java.util.Optional"},
        {"Serializable", "java.io.Serializable"},
        {"UUID", "java.util.UUID"},
        {"Object", "java.lang.Object"},
    };

    private Annotations() {
    }

    /**
     * 是否是持久层注解 (MyBatis / MyBatis-Plus / JPA / Hibernate)。
     */
    public static boolean isPersistenceAnnotation(String qualifiedName, String simpleName) {
        if (qualifiedName != null && !qualifiedName.isEmpty()) {
            for (String prefix : PERSISTENCE_PREFIXES) {
                if (qualifiedName.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return simpleName != null && PERSISTENCE_SIMPLE_NAMES.contains(simpleName);
    }

    /**
     * 是否是 Lombok 注解。
     */
    public static boolean isLombokAnnotation(String qualifiedName, String simpleName) {
        if (qualifiedName != null && !qualifiedName.isEmpty()) {
            return qualifiedName.startsWith("lombok.");
        }
        return simpleName != null && LOMBOK_SIMPLE_NAMES.contains(simpleName);
    }

    /**
     * 从一段类型文本里挑出需要 import 的常见 JDK 类型。
     *
     * <p>只在"整个单词"命中时才返回, 避免 {@code MyLocalDateWrapper} 这种被 {@code LocalDate} 误伤。</p>
     */
    public static Set<String> commonTypeImports(String typeText) {
        if (typeText == null || typeText.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String[] pair : COMMON_TYPES) {
            if (containsWord(typeText, pair[0])) {
                result.add(pair[1]);
            }
        }
        return result;
    }

    /**
     * 按标识符边界找子串: 前后不能是字母/数字/下划线。
     */
    static boolean containsWord(String text, String word) {
        int from = 0;
        while (true) {
            int index = text.indexOf(word, from);
            if (index < 0) {
                return false;
            }
            boolean leftOk = index == 0 || !isIdentifierChar(text.charAt(index - 1));
            int end = index + word.length();
            boolean rightOk = end >= text.length() || !isIdentifierChar(text.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = index + 1;
        }
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 供验证脚本列举名单用。
     */
    public static List<String> persistenceSimpleNames() {
        return Arrays.asList(PERSISTENCE_SIMPLE_NAMES.toArray(new String[0]));
    }
}

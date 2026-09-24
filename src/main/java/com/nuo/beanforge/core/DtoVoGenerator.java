package com.nuo.beanforge.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 核心生成器: 实体模型 -> 目标类源码文本。
 *
 * <p>纯函数, 不碰任何 IDE API —— 这样它可以被离线脚本大量断言,
 * 而不是靠"在 IDE 里点一下看看对不对"。</p>
 */
public final class DtoVoGenerator {

    /**
     * 一个缩进层级。
     */
    static final String INDENT = "    ";

    private DtoVoGenerator() {
    }

    /**
     * 生成目标类源码。
     */
    public static GenerationResult generate(EntityModel entity, GenerateOptions options) {
        List<String> warnings = new ArrayList<>();

        String targetPackage = options.getTargetPackage();
        if (targetPackage == null || targetPackage.trim().isEmpty()) {
            targetPackage = entity.getPackageName();
        }
        String targetClassName = options.getTargetClassName();
        if (targetClassName == null || targetClassName.trim().isEmpty()) {
            targetClassName = entity.getClassName();
        }

        List<FieldModel> fields = pickFields(entity, options, warnings);
        List<String> imports = collectImports(entity, fields, targetPackage, targetClassName, options, warnings);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(targetPackage).append(";\n");
        sb.append("\n");
        for (String statement : imports) {
            sb.append(statement).append("\n");
        }
        if (!imports.isEmpty()) {
            sb.append("\n");
        }

        appendClassComment(sb, entity, options);

        for (String annotation : resolveClassAnnotations(entity, options)) {
            sb.append(annotation).append("\n");
        }

        sb.append("public class ").append(targetClassName);
        if (options.isSerializable()) {
            sb.append(" implements Serializable");
        }
        sb.append(" {\n");
        sb.append("\n");

        if (options.isSerializable()) {
            sb.append(INDENT).append("private static final long serialVersionUID = 1L;\n");
            sb.append("\n");
        }

        for (int i = 0; i < fields.size(); i++) {
            appendField(sb, fields.get(i), options);
            if (i < fields.size() - 1) {
                sb.append("\n");
            }
        }

        sb.append("}\n");

        return new GenerationResult(sb.toString(), warnings, fields);
    }

    /**
     * 挑出要写进新类的字段: 勾选过的、名字合法的。
     */
    private static List<FieldModel> pickFields(EntityModel entity, GenerateOptions options, List<String> warnings) {
        List<FieldModel> result = new ArrayList<>();
        for (FieldModel field : entity.getFields()) {
            if (!field.isSelected()) {
                continue;
            }
            if (!options.isIncludeSuperFields() && field.isFromSuper()) {
                continue;
            }
            if (field.getName() == null || field.getName().trim().isEmpty()) {
                warnings.add("有字段没填名字, 已跳过");
                continue;
            }
            if (field.getTypeText() == null || field.getTypeText().trim().isEmpty()) {
                warnings.add("字段 " + field.getName() + " 没填类型, 已跳过");
                continue;
            }
            if (!isValidJavaIdentifier(field.getName())) {
                warnings.add("字段名不合法, 已跳过: " + field.getName());
                continue;
            }
            result.add(field);
        }
        return result;
    }

    private static List<String> collectImports(EntityModel entity,
                                               List<FieldModel> fields,
                                               String targetPackage,
                                               String targetClassName,
                                               GenerateOptions options,
                                               List<String> warnings) {
        Set<String> needed = new LinkedHashSet<>(entity.getImportHints());

        // 注解的 import 只跟着"过滤后真正保留下来的注解"走, 否则去掉注解后会多出无用 import
        for (AnnotationModel annotation : filterAnnotations(entity.getClassAnnotations(), options)) {
            if (annotation.getQualifiedName() != null) {
                needed.add(annotation.getQualifiedName());
            }
        }

        for (FieldModel field : fields) {
            needed.addAll(field.getImportHints());
            for (AnnotationModel annotation : filterAnnotations(field.getAnnotations(), options)) {
                if (annotation.getQualifiedName() != null) {
                    needed.add(annotation.getQualifiedName());
                }
            }
            if (field.isManual()) {
                needed.addAll(Annotations.commonTypeImports(field.getTypeText()));
            }
        }

        for (String lombokImport : lombokImports(options)) {
            needed.add(lombokImport);
        }
        if (options.isSerializable()) {
            needed.add("java.io.Serializable");
        }

        ImportCollector.Result result = ImportCollector.collect(needed, targetPackage, targetClassName);
        warnings.addAll(result.getWarnings());
        return result.getStatements();
    }

    /**
     * 保留的类注解原文 (持久层 / Lombok 注解按选项剔除)。
     */
    static List<String> resolveClassAnnotations(EntityModel entity, GenerateOptions options) {
        List<String> result = new ArrayList<>();
        for (AnnotationModel annotation : filterAnnotations(entity.getClassAnnotations(), options)) {
            result.add(annotation.getText());
        }
        result.addAll(lombokAnnotations(options));
        return result;
    }

    /**
     * 生成的 Lombok 注解列表 (顺序固定, 便于断言)。
     */
    static List<String> lombokAnnotations(GenerateOptions options) {
        List<String> result = new ArrayList<>();
        if (options.isLombokData()) {
            result.add("@Data");
        } else if (options.isLombokGetterSetter()) {
            result.add("@Getter");
            result.add("@Setter");
        }
        if (options.isLombokNoArgsConstructor()) {
            result.add("@NoArgsConstructor");
        }
        if (options.isLombokAllArgsConstructor()) {
            result.add("@AllArgsConstructor");
        }
        if (options.isLombokAccessorsChain()) {
            result.add("@Accessors(chain = true)");
        }
        if (options.isLombokBuilder()) {
            result.add("@Builder");
        }
        return result;
    }

    static List<String> lombokImports(GenerateOptions options) {
        List<String> result = new ArrayList<>();
        if (options.isLombokData()) {
            result.add("lombok.Data");
        } else if (options.isLombokGetterSetter()) {
            result.add("lombok.Getter");
            result.add("lombok.Setter");
        }
        if (options.isLombokNoArgsConstructor()) {
            result.add("lombok.NoArgsConstructor");
        }
        if (options.isLombokAllArgsConstructor()) {
            result.add("lombok.AllArgsConstructor");
        }
        if (options.isLombokAccessorsChain()) {
            result.add("lombok.experimental.Accessors");
        }
        if (options.isLombokBuilder()) {
            result.add("lombok.Builder");
        }
        return result;
    }

    /**
     * 按选项剔除不该带过去的注解 (公开出来是为了让离线脚本能直接断言过滤结果)。
     */
    public static List<AnnotationModel> filterAnnotations(List<AnnotationModel> annotations, GenerateOptions options) {
        List<AnnotationModel> result = new ArrayList<>();
        for (AnnotationModel annotation : annotations) {
            if (options.isRemovePersistenceAnnotations()
                && Annotations.isPersistenceAnnotation(annotation.getQualifiedName(), annotation.getSimpleName())) {
                continue;
            }
            if (options.isRemoveLombokAnnotations()
                && Annotations.isLombokAnnotation(annotation.getQualifiedName(), annotation.getSimpleName())) {
                continue;
            }
            result.add(annotation);
        }
        return result;
    }

    private static void appendClassComment(StringBuilder sb, EntityModel entity, GenerateOptions options) {
        String raw = options.getClassComment();
        List<String> lines;
        if (raw != null && !raw.trim().isEmpty()) {
            lines = CommentUtils.ofLines(raw);
        } else if (options.isKeepComments()) {
            lines = new ArrayList<>(entity.getClassComments());
        } else {
            lines = new ArrayList<>();
        }
        if (lines.isEmpty()) {
            return;
        }
        sb.append(CommentUtils.renderJavadoc(lines, "")).append("\n");
    }

    private static void appendField(StringBuilder sb, FieldModel field, GenerateOptions options) {
        if (options.isKeepComments() && !field.getComments().isEmpty()) {
            sb.append(CommentUtils.renderJavadoc(field.getComments(), INDENT)).append("\n");
        }
        for (AnnotationModel annotation : filterAnnotations(field.getAnnotations(), options)) {
            sb.append(INDENT).append(annotation.getText()).append("\n");
        }
        sb.append(INDENT).append("private ").append(field.getTypeText().trim())
            .append(" ").append(field.getName()).append(";\n");
    }

    /**
     * 校验是不是一个合法的 Java 标识符 (且不是关键字)。
     */
    public static boolean isValidJavaIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return !KEYWORDS.contains(name);
    }

    private static final Set<String> KEYWORDS = new LinkedHashSet<>(Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
    ));
}

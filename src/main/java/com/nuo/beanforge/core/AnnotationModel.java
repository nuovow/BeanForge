package com.nuo.beanforge.core;

/**
 * 一个注解的轻量描述。
 *
 * <p>{@code qualifiedName} 在 PSI 解析不出类时会是 null (例如注解类不在项目 classpath 上),
 * 这时候只能靠 {@code simpleName} 兜底判断, 所以判定逻辑必须两种信息都能用。</p>
 */
public class AnnotationModel {

    private final String simpleName;
    private final String qualifiedName;
    /**
     * 注解原文 (含参数), 用于"保留下来的注解"原样输出到新文件里。
     */
    private final String text;

    public AnnotationModel(String simpleName, String qualifiedName, String text) {
        this.simpleName = simpleName == null ? "" : simpleName;
        this.qualifiedName = qualifiedName;
        this.text = text == null ? "" : text;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "@" + simpleName + (qualifiedName == null ? "" : " (" + qualifiedName + ")");
    }
}

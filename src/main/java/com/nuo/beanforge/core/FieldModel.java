package com.nuo.beanforge.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个待生成字段。
 *
 * <p>带有 UI 状态 ({@link #selected}), 但 core 层的生成器只看选中的字段, 不关心界面。</p>
 */
public class FieldModel {

    private String name;
    /**
     * 类型文本, 如 {@code Long}、{@code List<String>}、{@code BigDecimal}。
     */
    private String typeText;
    /**
     * 已经归一化好的注释行 (不含注释符号, 不含前导 *)。空列表表示没注释。
     */
    private List<String> comments = new ArrayList<>();
    /**
     * 原文保留下来的注解 (持久层注解与 Lombok 注解已在提取阶段被剔除)。
     */
    private List<AnnotationModel> annotations = new ArrayList<>();
    /**
     * 该字段类型涉及的全限定类名, 用于生成 import。
     */
    private List<String> importHints = new ArrayList<>();

    private boolean selected = true;
    /**
     * 用户在界面上手工添加的字段 (这类字段没有原始注解, 也没有 import 线索)。
     */
    private boolean manual = false;
    /**
     * 是否来自父类 (继承来的字段)。
     */
    private boolean fromSuper = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTypeText() {
        return typeText;
    }

    public void setTypeText(String typeText) {
        this.typeText = typeText;
    }

    public List<String> getComments() {
        return comments;
    }

    public void setComments(List<String> comments) {
        this.comments = comments == null ? new ArrayList<>() : comments;
    }

    public List<AnnotationModel> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<AnnotationModel> annotations) {
        this.annotations = annotations == null ? new ArrayList<>() : annotations;
    }

    public List<String> getImportHints() {
        return importHints;
    }

    public void setImportHints(List<String> importHints) {
        this.importHints = importHints == null ? new ArrayList<>() : importHints;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isManual() {
        return manual;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }

    public boolean isFromSuper() {
        return fromSuper;
    }

    public void setFromSuper(boolean fromSuper) {
        this.fromSuper = fromSuper;
    }

    /**
     * 字段注释的纯文本 (多行用换行拼), 供界面表格里显示。
     */
    public String commentText() {
        return String.join(" ", comments);
    }
}

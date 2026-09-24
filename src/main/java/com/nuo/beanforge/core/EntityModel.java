package com.nuo.beanforge.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个待转换的实体类。
 */
public class EntityModel {

    private String packageName;
    private String className;
    private List<String> classComments = new ArrayList<>();
    /**
     * 原文保留的类级注解 (持久层注解与 Lombok 注解已剔除)。
     */
    private List<AnnotationModel> classAnnotations = new ArrayList<>();
    private List<FieldModel> fields = new ArrayList<>();
    /**
     * 类级注解涉及的全限定类名, 用于生成 import。
     */
    private List<String> importHints = new ArrayList<>();

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getQualifiedName() {
        if (packageName == null || packageName.isEmpty()) {
            return className;
        }
        return packageName + "." + className;
    }

    public List<String> getClassComments() {
        return classComments;
    }

    public void setClassComments(List<String> classComments) {
        this.classComments = classComments == null ? new ArrayList<>() : classComments;
    }

    public List<AnnotationModel> getClassAnnotations() {
        return classAnnotations;
    }

    public void setClassAnnotations(List<AnnotationModel> classAnnotations) {
        this.classAnnotations = classAnnotations == null ? new ArrayList<>() : classAnnotations;
    }

    public List<FieldModel> getFields() {
        return fields;
    }

    public void setFields(List<FieldModel> fields) {
        this.fields = fields == null ? new ArrayList<>() : fields;
    }

    public List<String> getImportHints() {
        return importHints;
    }

    public void setImportHints(List<String> importHints) {
        this.importHints = importHints == null ? new ArrayList<>() : importHints;
    }
}

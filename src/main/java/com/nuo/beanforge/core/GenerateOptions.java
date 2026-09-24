package com.nuo.beanforge.core;

/**
 * 生成开关。默认值就是"按下 Alt+Enter 直接回车"时得到的东西:
 * 去持久层注解、留注释、Lombok @Data + 无参/全参构造。
 */
public class GenerateOptions {

    private String targetPackage;
    private String targetClassName;
    /**
     * 类注释正文 (可编辑)。null 表示用自动拼的 "Xxx 由 Xxx 生成"。
     */
    private String classComment;

    private boolean removePersistenceAnnotations = true;
    private boolean removeLombokAnnotations = true;
    private boolean keepComments = true;
    private boolean includeSuperFields = false;

    private boolean lombokData = true;
    /**
     * 不用 @Data 时, 退而求其次只加 @Getter/@Setter。
     */
    private boolean lombokGetterSetter = false;
    private boolean lombokNoArgsConstructor = true;
    private boolean lombokAllArgsConstructor = true;
    private boolean lombokBuilder = false;
    private boolean lombokAccessorsChain = false;
    private boolean serializable = false;

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    public String getTargetClassName() {
        return targetClassName;
    }

    public void setTargetClassName(String targetClassName) {
        this.targetClassName = targetClassName;
    }

    public String getClassComment() {
        return classComment;
    }

    public void setClassComment(String classComment) {
        this.classComment = classComment;
    }

    public boolean isRemovePersistenceAnnotations() {
        return removePersistenceAnnotations;
    }

    public void setRemovePersistenceAnnotations(boolean removePersistenceAnnotations) {
        this.removePersistenceAnnotations = removePersistenceAnnotations;
    }

    public boolean isRemoveLombokAnnotations() {
        return removeLombokAnnotations;
    }

    public void setRemoveLombokAnnotations(boolean removeLombokAnnotations) {
        this.removeLombokAnnotations = removeLombokAnnotations;
    }

    public boolean isKeepComments() {
        return keepComments;
    }

    public void setKeepComments(boolean keepComments) {
        this.keepComments = keepComments;
    }

    public boolean isIncludeSuperFields() {
        return includeSuperFields;
    }

    public void setIncludeSuperFields(boolean includeSuperFields) {
        this.includeSuperFields = includeSuperFields;
    }

    public boolean isLombokData() {
        return lombokData;
    }

    public void setLombokData(boolean lombokData) {
        this.lombokData = lombokData;
    }

    public boolean isLombokGetterSetter() {
        return lombokGetterSetter;
    }

    public void setLombokGetterSetter(boolean lombokGetterSetter) {
        this.lombokGetterSetter = lombokGetterSetter;
    }

    public boolean isLombokNoArgsConstructor() {
        return lombokNoArgsConstructor;
    }

    public void setLombokNoArgsConstructor(boolean lombokNoArgsConstructor) {
        this.lombokNoArgsConstructor = lombokNoArgsConstructor;
    }

    public boolean isLombokAllArgsConstructor() {
        return lombokAllArgsConstructor;
    }

    public void setLombokAllArgsConstructor(boolean lombokAllArgsConstructor) {
        this.lombokAllArgsConstructor = lombokAllArgsConstructor;
    }

    public boolean isLombokBuilder() {
        return lombokBuilder;
    }

    public void setLombokBuilder(boolean lombokBuilder) {
        this.lombokBuilder = lombokBuilder;
    }

    public boolean isLombokAccessorsChain() {
        return lombokAccessorsChain;
    }

    public void setLombokAccessorsChain(boolean lombokAccessorsChain) {
        this.lombokAccessorsChain = lombokAccessorsChain;
    }

    public boolean isSerializable() {
        return serializable;
    }

    public void setSerializable(boolean serializable) {
        this.serializable = serializable;
    }
}

package com.nuo.beanforge.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成结果: 源码文本 + 需要提醒用户的问题 (类型名冲突、字段名不合法之类)。
 */
public class GenerationResult {

    private final String source;
    private final List<String> warnings;
    private final List<FieldModel> usedFields;

    public GenerationResult(String source, List<String> warnings, List<FieldModel> usedFields) {
        this.source = source;
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
        this.usedFields = usedFields == null ? new ArrayList<>() : usedFields;
    }

    public String getSource() {
        return source;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * 实际写进新类的字段 (已勾选、且名字合法), 供提示信息用。
     */
    public List<FieldModel> getUsedFields() {
        return usedFields;
    }
}

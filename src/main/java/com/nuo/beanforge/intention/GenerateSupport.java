package com.nuo.beanforge.intention;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.nuo.beanforge.core.DtoVoGenerator;
import com.nuo.beanforge.core.GenerateOptions;
import com.nuo.beanforge.core.GenerationResult;
import com.nuo.beanforge.psi.GeneratedWriter;
import com.nuo.beanforge.ui.FieldPickerDialog;
import org.jetbrains.annotations.Nullable;

/**
 * 两个意图共用的后半程: 弹对话框 -> 生成源码 -> 写出 -> 反馈。
 */
final class GenerateSupport {

    private GenerateSupport() {
    }

    static void open(Project project,
                     @Nullable Editor editor,
                     @Nullable PsiClass entity,
                     @Nullable String initialClassName) {
        FieldPickerDialog dialog = new FieldPickerDialog(project, entity, initialClassName);
        if (!dialog.showAndGet()) {
            return;
        }
        PsiClass chosen = dialog.getEntity();
        if (chosen == null) {
            return;
        }
        GenerateOptions options = dialog.buildOptions();
        GenerationResult result = DtoVoGenerator.generate(dialog.getEntityModel(), options);

        PsiFile created = GeneratedWriter.write(project, chosen,
            options.getTargetPackage(), options.getTargetClassName(), result.getSource());
        if (created == null) {
            Messages.showWarningDialog(project,
                "目标类已存在, 没有覆盖:\n"
                    + options.getTargetPackage() + "." + options.getTargetClassName(),
                "BeanForge");
            return;
        }

        String message = "已生成 " + options.getTargetClassName()
            + ", 保留 " + result.getUsedFields().size() + " 个字段";
        if (!result.getWarnings().isEmpty()) {
            Messages.showWarningDialog(project,
                message + "\n\n" + String.join("\n", result.getWarnings()), "BeanForge");
        } else if (editor != null && !editor.isDisposed()) {
            HintManager.getInstance().showInformationHint(editor, message);
        }
    }
}

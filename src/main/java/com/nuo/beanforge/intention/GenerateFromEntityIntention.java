package com.nuo.beanforge.intention;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 光标在实体类里按 Alt+Enter: 生成 DTO / VO。
 */
public class GenerateFromEntityIntention extends PsiElementBaseIntentionAction {

    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (psiClass == null) {
            return;
        }
        GenerateSupport.open(project, editor, psiClass, null);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (psiClass == null || psiClass.getName() == null) {
            return false;
        }
        if (psiClass.isInterface() || psiClass.isEnum() || psiClass.isAnnotationType()) {
            return false;
        }
        // 内部类不打扰: 光标在内层类里时, 外层类也可能被识别出来, 容易误触
        if (psiClass.getContainingClass() != null) {
            return false;
        }
        return hasInstanceField(psiClass);
    }

    /**
     * 只有实例字段才算"有内容可搬" —— 全是 static 常量的类 (工具类、常量类) 不该出现这个选项。
     */
    private static boolean hasInstanceField(PsiClass psiClass) {
        for (PsiField field : psiClass.getFields()) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 要弹对话框, 不能包在 write action 里。
     */
    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @NotNull
    @Override
    public String getText() {
        return "BeanForge: 生成 DTO / VO";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "BeanForge";
    }
}

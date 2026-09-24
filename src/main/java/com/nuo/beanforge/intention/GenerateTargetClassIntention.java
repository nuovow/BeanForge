package com.nuo.beanforge.intention;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.nuo.beanforge.core.DtoVoGenerator;
import com.nuo.beanforge.core.Naming;
import com.nuo.beanforge.psi.EntityLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 光标停在一个<b>还不存在的类型</b>上时 (例如 {@code Result<LoginVo> login} 里的 LoginVo),
 * 按 Alt+Enter 直接从实体把这个类生出来 —— 取代 IDEA 自带的"创建类 'LoginVo'"那条空壳路径。
 */
public class GenerateTargetClassIntention extends PsiElementBaseIntentionAction {

    /**
     * 实体类常见的后缀, 用来从目标类名反推实体。
     */
    private static final List<String> ENTITY_SUFFIX_CANDIDATES =
        Arrays.asList("Entity", "DO", "PO", "POJO");

    /**
     * isAvailable 会把光标处的类名暂存在这里给 getText 用。
     *
     * <p>IDEA 取意图菜单时是先在同一个 EDT 线程上跑 isAvailable, 再调 getText, 所以这样传值是安全的;
     * 好处是菜单里能显示具体类名 (跟 IDEA 自带的"创建类 'X'"一样直观)。</p>
     */
    private String pendingClassName;

    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
        String className = unresolvedTypeName(element);
        if (className == null) {
            return;
        }
        GenerateSupport.open(project, editor, resolveEntity(project, className), className);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
        pendingClassName = unresolvedTypeName(element);
        return pendingClassName != null;
    }

    /**
     * 光标处的类型名, 且这个类型目前解析不到 (也就是"还没创建")。
     *
     * <p>必须是类型位置 —— 变量名、方法名也是 PsiJavaCodeReferenceElement,
     * 所以入口限定在 {@link PsiTypeElement} 上, 只认类型引用。</p>
     */
    @Nullable
    static String unresolvedTypeName(@NotNull PsiElement element) {
        PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class, false);
        if (typeElement == null) {
            return null;
        }
        PsiJavaCodeReferenceElement reference = typeElement.getInnermostComponentReferenceElement();
        if (reference == null || reference.resolve() != null) {
            return null;
        }
        String name = reference.getReferenceName();
        if (name == null || name.length() < 2) {
            return null;
        }
        if (!Character.isUpperCase(name.charAt(0))) {
            return null;
        }
        return DtoVoGenerator.isValidJavaIdentifier(name) ? name : null;
    }

    /**
     * 从目标类名反推实体: {@code LoginVo} -> {@code LoginEntity} / {@code LoginDO} / {@code Login}。
     *
     * <p>推不出来就返回 null, 对话框里仍可由用户自己挑实体。</p>
     */
    @Nullable
    static PsiClass resolveEntity(@NotNull Project project, @NotNull String targetClassName) {
        String base = Naming.stripKnownSuffix(targetClassName);
        if (base.isEmpty()) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        for (String suffix : ENTITY_SUFFIX_CANDIDATES) {
            candidates.add(base + suffix);
        }
        candidates.add(base);
        for (String candidate : candidates) {
            List<PsiClass> found = EntityLocator.entitiesByShortName(project, candidate);
            if (!found.isEmpty()) {
                return found.get(0);
            }
        }
        return null;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @NotNull
    @Override
    public String getText() {
        if (pendingClassName == null) {
            return "BeanForge: 从实体生成这个类";
        }
        return "BeanForge: 从实体生成 \"" + pendingClassName + "\"";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "BeanForge";
    }
}

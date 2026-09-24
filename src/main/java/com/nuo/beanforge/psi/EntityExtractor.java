package com.nuo.beanforge.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.nuo.beanforge.core.AnnotationModel;
import com.nuo.beanforge.core.CommentUtils;
import com.nuo.beanforge.core.EntityModel;
import com.nuo.beanforge.core.FieldModel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 PsiClass 抽出一个 {@link EntityModel}。
 *
 * <p>这一层刻意做得很薄: 只做"读 PSI"的体力活, 所有判断 (哪些注解要去掉、注释怎么归一化)
 * 都放在 core 里, 因为 core 能离线断言, PSI 不能。</p>
 */
public final class EntityExtractor {

    private EntityExtractor() {
    }

    /**
     * @param includeSuperFields 是否连父类继承来的字段一起带上
     */
    @NotNull
    public static EntityModel extract(@NotNull PsiClass psiClass, boolean includeSuperFields) {
        EntityModel model = new EntityModel();
        model.setPackageName(packageNameOf(psiClass));
        model.setClassName(psiClass.getName() == null ? "" : psiClass.getName());

        model.setClassComments(commentsOf(psiClass.getDocComment()));
        model.setClassAnnotations(toModels(psiClass.getAnnotations()));

        List<FieldModel> fields = new ArrayList<>();
        PsiField[] allFields = includeSuperFields ? psiClass.getAllFields() : psiClass.getFields();
        Set<String> seen = new LinkedHashSet<>();
        for (PsiField field : allFields) {
            // static 常量 (serialVersionUID、各类 CONSTANT) 不该进 DTO
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            FieldModel fieldModel = toModel(field);
            if (fieldModel.getName() == null || fieldModel.getName().isEmpty()) {
                continue;
            }
            // getAllFields 在实现同一个接口的多个父接口里可能给出重名项
            if (!seen.add(fieldModel.getName())) {
                continue;
            }
            fieldModel.setFromSuper(field.getContainingClass() != psiClass);
            fields.add(fieldModel);
        }
        model.setFields(fields);
        return model;
    }

    @NotNull
    private static FieldModel toModel(@NotNull PsiField field) {
        FieldModel model = new FieldModel();
        model.setName(field.getName());
        model.setTypeText(field.getType().getPresentableText());
        model.setComments(commentsOf(field.getDocComment(), adjacentLineComment(field)));
        model.setAnnotations(toModels(field.getAnnotations()));

        Set<String> imports = new LinkedHashSet<>();
        collectTypeImports(field.getType(), imports);
        model.setImportHints(new ArrayList<>(imports));
        return model;
    }

    /**
     * 递归收集一个类型用到的所有类全限定名 (泛型参数、数组元素、通配符上界都要)。
     */
    static void collectTypeImports(PsiType type, Set<String> out) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiClass resolved = classType.resolve();
            if (resolved != null) {
                String qualifiedName = resolved.getQualifiedName();
                if (qualifiedName != null) {
                    out.add(qualifiedName);
                }
            }
            for (PsiType parameter : classType.getParameters()) {
                collectTypeImports(parameter, out);
            }
        } else if (type instanceof PsiArrayType) {
            collectTypeImports(((PsiArrayType) type).getComponentType(), out);
        } else if (type instanceof PsiWildcardType) {
            PsiWildcardType wildcard = (PsiWildcardType) type;
            if (wildcard.getBound() != null) {
                collectTypeImports(wildcard.getBound(), out);
            }
        }
    }

    @NotNull
    private static List<AnnotationModel> toModels(PsiAnnotation[] annotations) {
        List<AnnotationModel> result = new ArrayList<>();
        for (PsiAnnotation annotation : annotations) {
            String qualifiedName = annotation.getQualifiedName();
            String simpleName = simpleNameOf(annotation);
            result.add(new AnnotationModel(simpleName, qualifiedName, annotation.getText()));
        }
        return result;
    }

    private static String simpleNameOf(PsiAnnotation annotation) {
        PsiElement reference = annotation.getNameReferenceElement();
        String text = reference != null ? reference.getText() : annotation.getText();
        if (text == null) {
            return "";
        }
        int lastDot = text.lastIndexOf('.');
        return lastDot < 0 ? text : text.substring(lastDot + 1);
    }

    /**
     * 字段注释: 优先 Javadoc, 没有再找紧邻的行注释 (前置的或同一行行尾的)。
     */
    private static List<String> commentsOf(PsiDocComment docComment, PsiComment lineComment) {
        if (docComment != null) {
            return CommentUtils.fromJavadoc(docComment.getText());
        }
        if (lineComment != null) {
            return CommentUtils.fromLineComment(lineComment.getText());
        }
        return new ArrayList<>();
    }

    private static List<String> commentsOf(PsiDocComment docComment) {
        return commentsOf(docComment, null);
    }

    /**
     * 找字段旁边紧邻的行注释。
     *
     * <p>往前跨过"不含换行的空白", 往后同理 —— 后者就是 {@code private Long id; // 主键} 这种行尾注释。</p>
     */
    private static PsiComment adjacentLineComment(PsiField field) {
        PsiElement prev = skipInlineWhitespaceBackward(field.getPrevSibling());
        if (isLineComment(prev)) {
            return (PsiComment) prev;
        }
        PsiElement next = skipInlineWhitespaceForward(field.getNextSibling());
        if (isLineComment(next)) {
            return (PsiComment) next;
        }
        return null;
    }

    private static PsiElement skipInlineWhitespaceBackward(PsiElement element) {
        PsiElement current = element;
        while (current != null && current.getTextLength() > 0
            && current.getText().trim().isEmpty() && !current.getText().contains("\n")) {
            current = current.getPrevSibling();
        }
        return current;
    }

    private static PsiElement skipInlineWhitespaceForward(PsiElement element) {
        PsiElement current = element;
        while (current != null && current.getTextLength() > 0
            && current.getText().trim().isEmpty() && !current.getText().contains("\n")) {
            current = current.getNextSibling();
        }
        return current;
    }

    private static boolean isLineComment(PsiElement element) {
        return element instanceof PsiComment && !(element instanceof PsiDocComment);
    }

    /**
     * 取类所在包名。
     */
    public static String packageNameOf(@NotNull PsiClass psiClass) {
        PsiFile file = psiClass.getContainingFile();
        if (file instanceof PsiJavaFile) {
            return ((PsiJavaFile) file).getPackageName();
        }
        // 兜底: 从全限定名里切
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName != null) {
            int lastDot = qualifiedName.lastIndexOf('.');
            if (lastDot > 0) {
                return qualifiedName.substring(0, lastDot);
            }
        }
        return "";
    }

    /**
     * 按全限定名在项目里找类。
     */
    public static PsiClass findClass(@NotNull Project project, String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return null;
        }
        return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
    }
}

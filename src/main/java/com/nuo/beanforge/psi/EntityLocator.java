package com.nuo.beanforge.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 找出项目里可以当作"实体"的类。
 *
 * <p>没有可靠的元数据能证明"这个类是实体", 所以用三条启发式:
 * 带持久层注解、类名带 Entity/DO/PO 后缀、在 entity/domain/model 这类包下。
 * 命中任意一条就算候选 —— 宁可多列几个让用户选, 也不要漏。</p>
 */
public final class EntityLocator {

    private static final List<String> ENTITY_PACKAGE_TAILS = Arrays.asList(
        "entity", "entities", "domain", "model", "po", "pojo", "dataobject", "bean", "beans", "do"
    );

    private static final List<String> ENTITY_CLASS_SUFFIXES = Arrays.asList(
        "Entity", "POJO", "DO", "PO"
    );

    private EntityLocator() {
    }

    /**
     * 按简单名查类 (走名字索引, 很快)。
     */
    @NotNull
    public static List<PsiClass> byShortName(@NotNull Project project, @NotNull String simpleName) {
        if (simpleName.isEmpty()) {
            return Collections.emptyList();
        }
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiClass[] found = PsiShortNamesCache.getInstance(project).getClassesByName(simpleName, scope);
        List<PsiClass> result = new ArrayList<>();
        for (PsiClass psiClass : found) {
            if (psiClass.getQualifiedName() != null) {
                result.add(psiClass);
            }
        }
        return result;
    }

    /**
     * 按简单名查类, 但只留看起来像实体的。
     */
    @NotNull
    public static List<PsiClass> entitiesByShortName(@NotNull Project project, @NotNull String simpleName) {
        List<PsiClass> entities = new ArrayList<>();
        for (PsiClass psiClass : byShortName(project, simpleName)) {
            if (looksLikeEntity(psiClass)) {
                entities.add(psiClass);
            }
        }
        return entities;
    }

    /**
     * 扫描项目源码根下的候选实体。
     *
     * <p>会遍历源码文件, 大项目上比较慢, 调用方必须放到后台线程 + ReadAction 里跑。</p>
     *
     * @param limit 最多返回多少个, 防止超大项目把界面撑爆
     */
    @NotNull
    public static List<PsiClass> scanEntities(@NotNull Project project, int limit) {
        List<PsiClass> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        PsiManager psiManager = PsiManager.getInstance(project);

        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        for (VirtualFile root : roots) {
            if (result.size() >= limit) {
                break;
            }
            VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    if (result.size() >= limit) {
                        return false;
                    }
                    if (file.isDirectory()) {
                        String name = file.getName();
                        // 跳过明显的无关目录, 省掉大量 PSI 解析
                        return !name.startsWith(".") && !"target".equals(name)
                            && !"build".equals(name) && !"out".equals(name);
                    }
                    if (!"java".equals(file.getExtension())) {
                        return true;
                    }
                    PsiFile psiFile = psiManager.findFile(file);
                    if (!(psiFile instanceof PsiJavaFile)) {
                        return true;
                    }
                    for (PsiClass psiClass : ((PsiJavaFile) psiFile).getClasses()) {
                        if (!looksLikeEntity(psiClass)) {
                            continue;
                        }
                        String qualifiedName = psiClass.getQualifiedName();
                        if (qualifiedName != null && seen.add(qualifiedName)) {
                            result.add(psiClass);
                        }
                    }
                    return true;
                }
            });
        }

        result.sort((left, right) -> {
            String leftName = left.getName() == null ? "" : left.getName();
            String rightName = right.getName() == null ? "" : right.getName();
            return leftName.compareTo(rightName);
        });
        return result;
    }

    /**
     * 三条启发式, 命中任意一条即认为是实体。
     */
    public static boolean looksLikeEntity(@NotNull PsiClass psiClass) {
        if (psiClass.isInterface() || psiClass.isEnum() || psiClass.isAnnotationType()) {
            return false;
        }
        String name = psiClass.getName();
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName == null) {
                continue;
            }
            if (qualifiedName.startsWith("com.baomidou.mybatisplus.annotation.")
                || qualifiedName.startsWith("javax.persistence.")
                || qualifiedName.startsWith("jakarta.persistence.")
                || qualifiedName.startsWith("org.apache.ibatis.")) {
                return true;
            }
        }
        for (String suffix : ENTITY_CLASS_SUFFIXES) {
            if (name.endsWith(suffix) && name.length() > suffix.length()) {
                return true;
            }
        }
        String packageName = psiClass.getQualifiedName();
        if (packageName != null) {
            int lastDot = packageName.lastIndexOf('.');
            if (lastDot > 0) {
                String tail = packageName.substring(0, lastDot);
                int tailDot = tail.lastIndexOf('.');
                String last = tailDot < 0 ? tail : tail.substring(tailDot + 1);
                if (ENTITY_PACKAGE_TAILS.contains(last.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
}

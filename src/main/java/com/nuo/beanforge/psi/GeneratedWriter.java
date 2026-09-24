package com.nuo.beanforge.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 把生成的源码落到磁盘上。
 *
 * <p>三条硬规则:
 * <ol>
 *   <li>目标文件已存在就<b>不动</b> (和 MybatisX 侧一致的"只创建新的"策略);</li>
 *   <li>整个创建 + 格式化过程包在一个 write command 里, 用户一次 Ctrl+Z 能全部撤销;</li>
 *   <li>用 PSI 建文件而不是直接写磁盘, 这样 IDEA 的索引、import 优化、撤销栈都是通的。</li>
 * </ol>
 */
public final class GeneratedWriter {

    private GeneratedWriter() {
    }

    /**
     * 先看目标文件在不在 (只读, 不创建目录)。
     *
     * @return 已存在时返回它的虚拟文件, 否则 null
     */
    @Nullable
    public static VirtualFile findExisting(@NotNull Project project,
                                           @NotNull PsiClass entity,
                                           @Nullable String targetPackage,
                                           @NotNull String className) {
        PsiDirectory directory = locateDirectory(project, entity, targetPackage, false);
        if (directory == null) {
            return null;
        }
        PsiFile existing = directory.findFile(className + ".java");
        return existing == null ? null : existing.getVirtualFile();
    }

    /**
     * 写出新类。
     *
     * @return 写成功返回新建的 PsiFile; 目标已存在或目录不可写时返回 null
     */
    @Nullable
    public static PsiFile write(@NotNull Project project,
                                @NotNull PsiClass entity,
                                @Nullable String targetPackage,
                                @NotNull String className,
                                @NotNull String source) {
        PsiFile[] holder = new PsiFile[1];
        WriteCommandAction.runWriteCommandAction(project, "BeanForge: 生成 " + className, null, () -> {
            PsiDirectory directory = locateDirectory(project, entity, targetPackage, true);
            if (directory == null || !directory.isWritable()) {
                return;
            }
            String fileName = className + ".java";
            if (directory.findFile(fileName) != null) {
                return;
            }
            PsiFile created = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, source);
            PsiFile added = (PsiFile) directory.add(created);
            // add 之后原对象已失效, 必须用返回的那个
            CodeStyleManager.getInstance(project).reformat(added);
            holder[0] = added;
        });

        if (holder[0] != null) {
            FileEditorManager.getInstance(project).openFile(holder[0].getVirtualFile(), true);
        }
        return holder[0];
    }

    /**
     * 定位目标包对应的目录。
     *
     * <p>以实体所在文件所属的源码根为基准 —— 生成物天然和实体在同一个 module / source root 下。</p>
     *
     * @param create 是否允许创建不存在的包目录。纯检查时必须传 false, 否则用户一取消
     *               就在磁盘上留下一串空目录。
     */
    @Nullable
    static PsiDirectory locateDirectory(@NotNull Project project,
                                        @NotNull PsiClass entity,
                                        @Nullable String targetPackage,
                                        boolean create) {
        PsiFile containingFile = entity.getContainingFile();
        if (containingFile == null) {
            return null;
        }
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiDirectory baseDir = null;

        VirtualFile entityVirtualFile = containingFile.getVirtualFile();
        if (entityVirtualFile != null) {
            VirtualFile sourceRoot = ProjectFileIndex.getInstance(project)
                .getSourceRootForFile(entityVirtualFile);
            if (sourceRoot != null) {
                baseDir = psiManager.findDirectory(sourceRoot);
            }
        }
        if (baseDir == null) {
            baseDir = containingFile.getParent();
        }
        if (baseDir == null) {
            return null;
        }
        if (targetPackage == null || targetPackage.trim().isEmpty()) {
            return baseDir;
        }

        // 逐级下探: create=false 时任何一级缺失就返回 null (只读检查, 绝不能建目录);
        // create=true 时补建缺失的那几级 (调用方保证在 write action 里)
        PsiDirectory current = baseDir;
        for (String segment : targetPackage.trim().split("\\.")) {
            if (current == null) {
                return null;
            }
            PsiDirectory next = current.findSubdirectory(segment);
            if (next == null) {
                if (!create) {
                    return null;
                }
                next = current.createSubdirectory(segment);
            }
            current = next;
        }
        return current;
    }
}

package com.nuo.beanforge.ui;

import com.nuo.beanforge.core.TypeCatalog;

import javax.swing.ComboBoxEditor;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 类型列的下拉框: 可编辑 + 敲字母即时过滤候选。
 *
 * <p>为什么单独抽出来: 可编辑 {@link JComboBox} 有两份状态 —— <b>编辑器里的文本</b>
 * 和 <b>model 里选中的项</b>。而"重建候选列表"这件事天生会破坏编辑器文本
 * (往空 model 里 {@code addItem}, Swing 会把第一项自动选中, 顺手把用户刚敲的
 * {@code Str} 换成 {@code String})。踩过的坑在 {@link ComboBoxValues} 里有完整记录。</p>
 *
 * <p>所以这里定死一套顺序: <b>先存文本 → 重建 model → 清选中 → 写回文本</b>,
 * 并且用 client property 做重入保护 (写回文本会触发编辑器的 DocumentListener,
 * 不设防就会无限递归过滤)。</p>
 *
 * <p>只依赖 Swing, 因此能在 headless 环境里用真组件断言。</p>
 */
public final class TypeFilterCombo {

    /**
     * 下拉里最多同时列多少条候选 (多了反而难挑)。
     */
    public static final int MAX_CANDIDATES = 12;

    private static final String FILTERING_KEY = "com.nuo.beanforge.typeFiltering";

    private TypeFilterCombo() {
    }

    /**
     * 造一个装好候选、且"什么都没选"的类型下拉框。
     */
    public static JComboBox<String> create() {
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.setToolTipText("输入字母可检索 (如 Loc -> LocalDateTime), 也支持 List<String> / Map<String, Long> / String[]");
        reload(combo, TypeCatalog.suggestions());
        return combo;
    }

    /**
     * 按编辑框里当前的文本过滤候选, 并保证文本原样保留。
     */
    public static void applyFilter(JComboBox<String> combo) {
        if (combo == null) {
            return;
        }
        String text = ComboBoxValues.displayedText(combo);
        // 输入为空 = 用户把搜索清掉了, 这时候要看到"全部候选" (不是被上限截断的前 12 条)
        List<String> matches = text.isEmpty()
            ? TypeCatalog.suggestions()
            : TypeCatalog.filter(text, MAX_CANDIDATES);
        if (matches.equals(currentItems(combo))) {
            // 候选没变就别动 model —— 多余的 removeAllItems/addItem 会把光标顶到末尾甚至吃掉刚敲的字
            return;
        }
        boolean previous = isFiltering(combo);
        combo.putClientProperty(FILTERING_KEY, Boolean.TRUE);
        try {
            combo.removeAllItems();
            for (String candidate : matches) {
                combo.addItem(candidate);
            }
            // addItem 的那一刻 Swing 已经偷偷选中了第一条, 这里明确清掉
            ComboBoxValues.clearSelection(combo);
            setEditorText(combo, text);
        } finally {
            combo.putClientProperty(FILTERING_KEY, previous ? Boolean.TRUE : null);
        }
    }

    /**
     * 过滤 + 弹出下拉。headless (无图形环境, 比如跑断言) 时静默跳过弹窗。
     */
    public static void applyFilterAndShowPopup(JComboBox<String> combo) {
        applyFilter(combo);
        showPopupSafely(combo);
    }

    /**
     * 弹出下拉。窗口系统不可用时不抛异常 —— 过滤逻辑本身在 headless 下仍然可测。
     *
     * <p>已经精确命中某个候选时不弹: 用户敲完了 {@code String}, 再弹一个只剩它自己的
     * 列表是干扰。</p>
     */
    public static void showPopupSafely(JComboBox<String> combo) {
        if (combo == null || GraphicsEnvironment.isHeadless()) {
            return;
        }
        String text = ComboBoxValues.displayedText(combo);
        if (!text.isEmpty() && text.equals(exactMatch(combo, text))) {
            combo.setPopupVisible(false);
            return;
        }
        if (combo.getItemCount() == 0) {
            combo.setPopupVisible(false);
            return;
        }
        try {
            combo.setPopupVisible(true);
        } catch (Throwable ignored) {
            // 无窗口系统 / 组件还没上屏: 忽略, 不影响编辑
        }
    }

    /**
     * 编辑框里当前的文本, 规范化后返回 (生成时真正要用的值)。
     */
    public static String commitText(JComboBox<String> combo) {
        if (combo == null) {
            return "";
        }
        return TypeCatalog.normalize(ComboBoxValues.displayedText(combo));
    }

    /**
     * @return 校验错误说明, 合法返回 {@code null}
     */
    public static String errorOf(JComboBox<String> combo) {
        return TypeCatalog.validate(commitText(combo));
    }

    public static boolean isFiltering(JComboBox<?> combo) {
        return combo != null && Boolean.TRUE.equals(combo.getClientProperty(FILTERING_KEY));
    }

    /**
     * 在"压住自动过滤"的前提下改组合框。
     *
     * <p>用于程序性地放值/换候选: 这类动作会改写编辑器文本, 从而触发
     * {@code DocumentListener} → 过滤 —— 但那是"用户在搜字"才该做的事。
     * 不压住的话, 双击进入编辑时"把单元格的值放进去"这一步会被当成用户输入,
     * 候选立刻被过滤成一小撮 (极端情况: 单元格值是自定义类型 → 候选直接空掉)。</p>
     */
    public static <T> T runSilently(JComboBox<String> combo, Supplier<T> action) {
        if (combo == null || action == null) {
            return null;
        }
        boolean previous = isFiltering(combo);
        combo.putClientProperty(FILTERING_KEY, Boolean.TRUE);
        try {
            return action.get();
        } finally {
            // 复位成"进来之前的值": 这个方法会被嵌在另一段压制的逻辑里调用, 不能一把置 null
            combo.putClientProperty(FILTERING_KEY, previous ? Boolean.TRUE : null);
        }
    }

    /**
     * 不关心返回值的 {@link #runSilently(JComboBox, Supplier)}。
     */
    public static void runSilently(JComboBox<String> combo, Runnable action) {
        runSilently(combo, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 把编辑器文本设成指定值, 光标放到末尾 (用户还要接着敲)。
     */
    static void setEditorText(JComboBox<?> combo, String text) {
        ComboBoxEditor editor = combo.getEditor();
        if (editor == null) {
            return;
        }
        String value = text == null ? "" : text;
        if (editor.getEditorComponent() instanceof JTextField) {
            JTextField field = (JTextField) editor.getEditorComponent();
            field.setText(value);
            field.setCaretPosition(value.length());
        } else {
            editor.setItem(value);
        }
    }

    /**
     * 用给定候选重装下拉, 结果保持"没选中"。
     *
     * <p>公开是因为它也是"扩充候选"的正规入口 (例如以后要把项目里已有的类型也塞进下拉)。</p>
     */
    public static void reload(JComboBox<String> combo, List<String> candidates) {
        boolean previous = isFiltering(combo);
        combo.putClientProperty(FILTERING_KEY, Boolean.TRUE);
        try {
            combo.removeAllItems();
            if (candidates != null) {
                for (String candidate : candidates) {
                    combo.addItem(candidate);
                }
            }
            ComboBoxValues.clearSelection(combo);
        } finally {
            combo.putClientProperty(FILTERING_KEY, previous ? Boolean.TRUE : null);
        }
    }

    private static List<String> currentItems(JComboBox<String> combo) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            items.add(combo.getItemAt(i));
        }
        return items;
    }

    /**
     * @return 下拉里与文本完全相同的候选项; 没有则返回 {@code null}
     */
    private static String exactMatch(JComboBox<String> combo, String text) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (text.equals(combo.getItemAt(i))) {
                return combo.getItemAt(i);
            }
        }
        return null;
    }
}

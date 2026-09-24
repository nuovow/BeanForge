package com.nuo.beanforge.ui;

import javax.swing.ComboBoxEditor;
import javax.swing.JComboBox;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 可编辑 {@link JComboBox} 的状态工具。
 *
 * <p>存在的理由只有一个: <b>可编辑组合框的"界面显示的值"和"model 里选中的值"是两份状态,
 * 它们会不同步</b>。踩到的具体坑是 —— 往空的 model 里 {@code addItem} 时, Swing 会把
 * 第一项<b>自动选中</b>; 如果这发生在"后台扫描完候选、批量刷进下拉"的时候 (那一刻我们
 * 有意屏蔽了切换事件), 界面上就显示着一个类名, 而业务代码拿到的仍然是"没选"。
 * 用户再点那一项也救不回来, 因为 JComboBox 对"重复选中同一个值"不发事件。</p>
 *
 * <p>不依赖 IDE API, 所以可以直接在 headless 环境里用真 Swing 组件断言这些行为。</p>
 */
public final class ComboBoxValues {

    private ComboBoxValues() {
    }

    /**
     * 组合框当前<b>显示</b>的值。
     *
     * <p>编辑器文本优先 (那才是用户眼睛看到的, 包括刚敲进去还没回车的), 编辑器为空才退回选中项。</p>
     *
     * @return 去掉首尾空白后的文本; 什么都没有时返回空串 (不是 null)
     */
    public static String displayedText(JComboBox<?> combo) {
        if (combo == null) {
            return "";
        }
        ComboBoxEditor editor = combo.getEditor();
        if (editor != null) {
            Object item = editor.getItem();
            if (item != null && !item.toString().trim().isEmpty()) {
                return item.toString().trim();
            }
        }
        Object selected = combo.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    /**
     * 恢复成"什么都没选"的样子: 显示清空, 同时把选中下标也清掉。
     *
     * <p>只清下标不够 —— 编辑器里的文本不会跟着清, 界面看起来还是选着的。</p>
     */
    public static void clearSelection(JComboBox<?> combo) {
        if (combo == null) {
            return;
        }
        combo.setSelectedIndex(-1);
        ComboBoxEditor editor = combo.getEditor();
        if (editor != null) {
            editor.setItem("");
        }
    }

    /**
     * 选中已有项, 没有就补一项再选中。
     *
     * @return 是否补了新项
     */
    public static boolean selectOrAdd(JComboBox<String> combo, String value) {
        if (combo == null || value == null) {
            return false;
        }
        String target = value.trim();
        if (target.isEmpty()) {
            return false;
        }
        boolean added = false;
        if (!contains(combo, target)) {
            combo.addItem(target);
            added = true;
        }
        combo.setSelectedItem(target);
        return added;
    }

    /**
     * 把一批候选刷进下拉, 并让组合框保持"加载前的样子"。
     *
     * <p>这是修掉那个假象的关键一步: 加载前没选, 加载后也必须还是没选。</p>
     *
     * @param keepText 加载前显示的值 (空串表示加载前没选); 非空时保持住 —— 因为 addItem
     *                 会打乱选中下标, 得重新选回来
     */
    public static void loadCandidates(JComboBox<String> combo, List<String> candidates, String keepText) {
        if (combo == null) {
            return;
        }
        Set<String> existing = new HashSet<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            existing.add(combo.getItemAt(i));
        }
        if (candidates != null) {
            for (String name : candidates) {
                // add 返回 true 说明之前没有: 顺带完成判重
                if (name != null && existing.add(name)) {
                    combo.addItem(name);
                }
            }
        }
        if (keepText != null && !keepText.trim().isEmpty()) {
            selectOrAdd(combo, keepText);
        } else {
            clearSelection(combo);
        }
    }

    private static boolean contains(JComboBox<String> combo, String value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equals(combo.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }
}

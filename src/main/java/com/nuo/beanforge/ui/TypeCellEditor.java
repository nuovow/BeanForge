package com.nuo.beanforge.ui;

import com.nuo.beanforge.core.TypeCatalog;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;

/**
 * "类型"列的单元格编辑器: 可编辑下拉 + 字母检索 + 提交前校验。
 *
 * <p>三层保险:</p>
 * <ol>
 *   <li>敲字母即时过滤候选并弹下拉 —— 想手打 {@code LocalDateTime} 时敲 {@code loc} 就够了</li>
 *   <li>提交时校验: 非法类型<b>拒绝结束编辑</b>, 并把编辑框标红 + 悬停提示原因</li>
 *   <li>表格还用 renderer 把非法类型染红 (见 {@code FieldPickerDialog}), 生成前的
 *       {@code doValidate} 再拦一道</li>
 * </ol>
 *
 * <p>两个必须自己接管的地方:</p>
 * <ul>
 *   <li>{@link #getCellEditorValue()} —— {@link DefaultCellEditor} 默认返回
 *       {@code combo.getSelectedItem()}, 用户手打一个不在候选里的类型时它是 {@code null},
 *       单元格会被写成空 ("看起来输了却没生效")。</li>
 *   <li>{@link #getTableCellEditorComponent(JTable, Object, boolean, int, int)} ——
 *       编辑器实例在整张表里是<b>复用</b>的, 上一次编辑留下的候选状态会跟着进下一个格子。</li>
 * </ul>
 */
public class TypeCellEditor extends DefaultCellEditor {

    private final JComboBox<String> combo;
    private final JTextField editorField;
    private Color normalForeground;

    public TypeCellEditor() {
        super(TypeFilterCombo.create());
        this.combo = cast(getComponent());
        this.editorField = editorFieldOf(combo);
        combo.setEditable(true);
        // 与界面提示"双击单元格可改类型"保持一致, 避免单击误触
        setClickCountToStart(2);
        if (editorField != null) {
            editorField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    onTextChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    onTextChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    onTextChanged();
                }
            });
            editorField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    // 编辑一开始就把候选摆出来 (双击即见), 不用再点一下箭头。
                    // 只能放这里: 进入编辑的那一刻编辑器组件还没上屏, 弹不出来。
                    TypeFilterCombo.showPopupSafely(combo);
                }
            });
        }
    }

    /**
     * 每次进入编辑: 把候选项恢复成全量, 并把单元格的值原样放上。
     *
     * <p>不这么做就会出现"双击无法选中" —— 编辑器实例是复用的, 上一次编辑把候选过滤成了
     * 一个子集 (用户输了个自定义类型的话就是空列表), 这个状态会跟着进下一个格子,
     * 于是双击打开看到的候选是残留的, 甚至是空的。用户想改类型都没得挑。</p>
     *
     * <p>整段都要压住自动过滤: {@code super} 会走
     * {@code setSelectedItem → configureEditor → editorField.setText}, 不压住的话
     * "放值"这个动作会被当成"用户在搜字", 候选立刻按单元格的值被过滤掉。</p>
     */
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                 int row, int column) {
        String text = value == null ? "" : value.toString();
        clearErrorVisual();
        Component component = TypeFilterCombo.runSilently(combo, () -> {
            TypeFilterCombo.reload(combo, TypeCatalog.suggestions());
            return super.getTableCellEditorComponent(table, value, isSelected, row, column);
        });
        // super 里那次 setSelectedItem 可能把编辑器文本顶掉, 值以单元格文本为准
        TypeFilterCombo.runSilently(combo, () -> TypeFilterCombo.setEditorText(combo, text));
        return component;
    }

    /**
     * 用户每敲一个字就重新过滤并弹下拉。
     *
     * <p><b>必须延后到当前事件处理之后, 不能同步做。</b> 实测的异常链:</p>
     * <pre>
     * DefaultCellEditor 放值 → JComboBox.setSelectedItem → configureEditor → editorField.setText
     *   → DocumentListener(这里) → applyFilter → removeAllItems()
     *   → 又 configureEditor → 又 setText
     *   → java.lang.IllegalStateException: Attempt to mutate in notification
     * </pre>
     * <p>在 Document 的通知回调里改同一个 Document, Swing 直接抛异常, 编辑器初始化就此中断
     * —— 表现就是"双击单元格打不开候选"。排到队列末尾执行即可避开。</p>
     */
    private void onTextChanged() {
        if (TypeFilterCombo.isFiltering(combo)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            // 这条延迟任务执行前, 程序可能又自己改了组合框 (例如进入编辑时重置候选),
            // 那种情况下不该当成"用户在搜字"
            if (TypeFilterCombo.isFiltering(combo)) {
                return;
            }
            TypeFilterCombo.applyFilterAndShowPopup(combo);
            // 打字过程中就把"非法"反馈出来, 不用等提交
            String error = TypeFilterCombo.errorOf(combo);
            if (error == null) {
                clearErrorVisual();
            } else {
                showErrorVisual(error);
            }
        });
    }

    @Override
    public Object getCellEditorValue() {
        return TypeFilterCombo.commitText(combo);
    }

    @Override
    public boolean stopCellEditing() {
        String error = TypeFilterCombo.errorOf(combo);
        if (error != null) {
            showErrorVisual("类型不合法: " + error);
            return false;
        }
        clearErrorVisual();
        return super.stopCellEditing();
    }

    @Override
    public void cancelCellEditing() {
        clearErrorVisual();
        super.cancelCellEditing();
    }

    /**
     * 把编辑框染成错误色并弹出原因 —— 只说"不能结束编辑"用户是看不懂的。
     */
    private void showErrorVisual(String message) {
        if (editorField == null) {
            return;
        }
        if (normalForeground == null) {
            normalForeground = editorField.getForeground();
        }
        editorField.setForeground(UiColors.ERROR);
        editorField.setToolTipText(message);
        showTooltipNow(message);
    }

    private void clearErrorVisual() {
        if (editorField == null) {
            return;
        }
        if (normalForeground != null) {
            editorField.setForeground(normalForeground);
        }
        editorField.setToolTipText(COMBO_HINT);
    }

    /**
     * 主动让 tooltip 显示出来: 用户此刻可能没在动鼠标, 等着看"为什么按不动"。
     */
    private void showTooltipNow(String message) {
        if (editorField == null || !editorField.isShowing()) {
            return;
        }
        try {
            editorField.setToolTipText(message);
            java.awt.Point center = new java.awt.Point(
                editorField.getWidth() / 2, editorField.getHeight() / 2);
            ToolTipManager.sharedInstance().mouseMoved(new MouseEvent(
                editorField, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
                0, center.x, center.y, 0, false));
        } catch (Throwable ignored) {
            // tooltip 只是锦上添花, 失败不影响功能
        }
    }

    private static final String COMBO_HINT =
        "输入字母可检索 (如 Loc -> LocalDateTime), 也支持 List<String> / Map<String, Long> / String[]";

    @SuppressWarnings("unchecked")
    private static JComboBox<String> cast(Object component) {
        return (JComboBox<String>) component;
    }

    private static JTextField editorFieldOf(JComboBox<String> combo) {
        if (combo.getEditor() != null
            && combo.getEditor().getEditorComponent() instanceof JTextField) {
            return (JTextField) combo.getEditor().getEditorComponent();
        }
        return null;
    }
}

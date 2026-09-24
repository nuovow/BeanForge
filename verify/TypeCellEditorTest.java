import com.nuo.beanforge.core.FieldModel;
import com.nuo.beanforge.core.TypeCatalog;
import com.nuo.beanforge.ui.FieldTableModel;
import com.nuo.beanforge.ui.TypeCellEditor;
import com.nuo.beanforge.ui.TypeFilterCombo;

import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.util.ArrayList;
import java.util.List;

/**
 * "双击类型单元格无法选中" 的行为验证。
 *
 * <p>用的都是真组件: 真 {@link JTable} + 真 {@link TypeCellEditor}, 并且走 JTable 双击时的
 * 真实调用路径 ({@code getTableCellEditorComponent}) —— 这个 bug 出在
 * <b>编辑器实例在整张表里是复用的</b> 这件事上, 拿假对象测不出来。</p>
 *
 * <p>能这么测的前提是 {@code TypeCellEditor} 没有任何 IDE API 依赖
 * (配色已收进纯 AWT 的 {@code UiColors}), 所以 headless 下就能把这条链路跑完。</p>
 */
public class TypeCellEditorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        enterEditTests();
        reuseLeakTests();
        customTypeTests();
        filteringStillWorksTests();
        validationTests();

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过, " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ================================================================ 1. 进入编辑

    private static void enterEditTests() {
        section("1. 双击进入编辑: 该看到全量候选 + 单元格当前的值");

        JTable table = buildTable();
        JComboBox<String> combo = enterEdit(table, 0);

        ok("进入编辑就有全部候选 (" + TypeCatalog.suggestions().size() + " 条), 不是一个空列表",
            combo.getItemCount() == TypeCatalog.suggestions().size());
        eq("编辑框里放的是单元格当前的值", editorText(combo), "String");
        ok("单元格值在候选里 -> 下拉把\"它自己\"高亮出来 (不是别的项)",
            "String".equals(combo.getSelectedItem()));

        JComboBox<String> empty = enterEdit(table, 1);
        ok("类型为空的格子同样给全量候选 (截图里就是这个场景)",
            empty.getItemCount() == TypeCatalog.suggestions().size());
        eq("空格子的编辑框是空的", editorText(empty), "");
    }

    // ================================================================ 2. 复现残留

    private static void reuseLeakTests() {
        section("2. 复现: 编辑器实例复用, 上一次过滤掉的候选会跟到下一个格子");

        JTable table = buildTable();
        JComboBox<String> combo = enterEdit(table, 0);

        // 在第一个格子里敲一个"候选表里没有"的类型 -> 候选被过滤空 (这是正常中间态)
        typeInto(combo, "MyCustomType");
        ok("输入自定义类型时候选被过滤空", combo.getItemCount() == 0);

        cellEditor(table).stopCellEditing();

        JComboBox<String> again = enterEdit(table, 1);
        ok("前后两次编辑拿到的是同一个 combo 实例 (所以状态会互相影响)", again == combo);
        eq("新格子的编辑框是空的", editorText(again), "");
        ok("候选必须恢复全量, 不能带着上一个格子的空列表进来",
            again.getItemCount() == TypeCatalog.suggestions().size());

        // 反过来也要成立: 上一次过滤到一个子集, 新格子不能被那个子集限制
        JTable second = buildTable();
        JComboBox<String> narrowed = enterEdit(second, 0);
        typeInto(narrowed, "loc");
        ok("上一格过滤到 3 条 (LocalDateTime/LocalDate/LocalTime)", narrowed.getItemCount() == 3);
        cellEditor(second).stopCellEditing();
        JComboBox<String> fresh = enterEdit(second, 2);
        ok("换到下一格 -> 候选回到全量, 不是那 3 条",
            fresh.getItemCount() == TypeCatalog.suggestions().size());
    }

    // ================================================================ 3. 自定义类型

    private static void customTypeTests() {
        section("3. 单元格里是自定义类型时, 候选也不能是空的");

        JTable table = buildTable();
        JComboBox<String> combo = enterEdit(table, 2);

        eq("文本是单元格里的自定义类型", editorText(combo), "MyCustomType");
        ok("候选仍是全量 (用户可能正是想把它改成一个常见类型)",
            combo.getItemCount() == TypeCatalog.suggestions().size());
        ok("自定义类型不在候选里 -> 选中项干干净净是 -1 (不能假装选中了别的类型)",
            combo.getSelectedIndex() == -1);
        ok("自定义类型本身是合法的, 不进错误态", TypeFilterCombo.errorOf(combo) == null);
    }

    // ================================================================ 4. 过滤没被破坏

    private static void filteringStillWorksTests() {
        section("4. 进入编辑后敲字仍然正常检索 (重置候选不能把过滤能力弄丢)");

        JTable table = buildTable();
        JComboBox<String> combo = enterEdit(table, 1);

        typeInto(combo, "loc");
        ok("敲 loc -> 3 条", combo.getItemCount() == 3);
        eq("文本保留", editorText(combo), "loc");

        typeInto(combo, "loca");
        ok("继续敲 -> 3 条", combo.getItemCount() == 3);

        typeInto(combo, "List<");
        ok("敲 List< -> 继续给 List<...> 候选", combo.getItemCount() == 4);

        // 清空后应该退回"全量候选", 而不是停在某个子集
        typeInto(combo, "");
        ok("清空输入 -> 候选退回全量", combo.getItemCount() == TypeCatalog.suggestions().size());
    }

    // ================================================================ 5. 校验仍在

    private static void validationTests() {
        section("5. 校验与取值没有被这次改动弄坏");

        JTable table = buildTable();
        JComboBox<String> combo = enterEdit(table, 1);
        TypeCellEditor editor = cellEditor(table);

        typeInto(combo, "List<");
        ok("非法类型 -> 不许结束编辑", !editor.stopCellEditing());
        eq("被拦下时文本还留着, 用户能接着改", editorText(combo), "List<");

        typeInto(combo, "List< Long >");
        ok("改合法了 -> 可以结束编辑", editor.stopCellEditing());
        eq("提交的值是规范化后的 (不是 null, 也不是原始带空格的)",
            String.valueOf(editor.getCellEditorValue()), "List<Long>");

        JComboBox<String> custom = enterEdit(table, 2);
        typeInto(custom, "MyCustomType");
        eq("手打的自定义类型取到的是文本本身 (不是选中项 null)",
            String.valueOf(cellEditor(table).getCellEditorValue()), "MyCustomType");
    }

    // ================================================================ 小工具

    private static JTable buildTable() {
        List<FieldModel> fields = new ArrayList<>();
        fields.add(field("username", "String"));
        // 第 1 行模拟"添加字段"出来的空行 —— 截图里双击的就是这种
        fields.add(field("memo", ""));
        fields.add(field("extra", "MyCustomType"));
        JTable table = new JTable(new FieldTableModel(fields));
        typeColumn(table).setCellEditor(new TypeCellEditor());
        return table;
    }

    /**
     * 走 JTable 双击进入编辑的真实调用路径, 返回那一刻的编辑器组件 (就是那个 combo)。
     */
    @SuppressWarnings("unchecked")
    private static JComboBox<String> enterEdit(JTable table, int row) {
        TableCellEditor editor = typeColumn(table).getCellEditor();
        Object value = table.getValueAt(row, FieldTableModel.COL_TYPE);
        JComboBox<String> combo = (JComboBox<String>) editor.getTableCellEditorComponent(
            table, value, false, row, FieldTableModel.COL_TYPE);
        drainEdt();
        return combo;
    }

    private static TableColumn typeColumn(JTable table) {
        return table.getColumnModel().getColumn(FieldTableModel.COL_TYPE);
    }

    private static TypeCellEditor cellEditor(JTable table) {
        return (TypeCellEditor) typeColumn(table).getCellEditor();
    }

    /**
     * 模拟用户敲字: 只改编辑器文本, 让挂在编辑器上的 DocumentListener 自己去过滤。
     *
     * <p>过滤是**刻意延后**执行的 (在 Document 通知回调里改同一个 Document 会抛
     * {@code IllegalStateException: Attempt to mutate in notification}), 所以这里要把
     * EDT 队列抽干, 断言才能看到过滤后的结果 —— 这也正是真实环境里的时序。</p>
     */
    private static void typeInto(JComboBox<String> combo, String text) {
        runOnEdt(() -> ((JTextField) combo.getEditor().getEditorComponent()).setText(text));
        drainEdt();
    }

    /**
     * 把当前排队的事件处理跑完 (空任务即可 —— Swing 是 FIFO 的)。
     */
    private static void drainEdt() {
        runOnEdt(() -> {
        });
    }

    private static void runOnEdt(Runnable action) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                action.run();
            } else {
                SwingUtilities.invokeAndWait(action);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String editorText(JComboBox<String> combo) {
        return ((JTextField) combo.getEditor().getEditorComponent()).getText();
    }

    private static FieldModel field(String name, String type) {
        FieldModel model = new FieldModel();
        model.setName(name);
        model.setTypeText(type);
        model.setSelected(true);
        return model;
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("-- " + title + " --");
    }

    private static void ok(String what, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [OK]   " + what);
        } else {
            failed++;
            System.out.println("  [FAIL] " + what);
        }
    }

    private static void eq(String what, String actual, String expected) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        if (same) {
            passed++;
            System.out.println("  [OK]   " + what + " -> \"" + actual + "\"");
        } else {
            failed++;
            System.out.println("  [FAIL] " + what + " -> 期望 \"" + expected + "\", 实际 \"" + actual + "\"");
        }
    }
}

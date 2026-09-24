import com.nuo.beanforge.ui.ComboBoxValues;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 针对"界面上明明选了实体、内部却报没选"这个 bug 的行为验证。
 *
 * <p>用的是<b>真的 {@link JComboBox}</b> —— 因为这个 bug 的根因就是 Swing 自身的行为
 * (往空 model 里 addItem 会自动选中第一项; 重复选中同一个值不发事件), 拿假对象测不出来。</p>
 *
 * <p>headless 直接跑: 只用组合框的 model / editor 状态, 不碰任何 native 组件。</p>
 */
public class ComboBoxValuesTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        section("1. 复现根因: 空下拉框加第一项会被自动选中");
        JComboBox<String> raw = editableCombo();
        raw.addItem("com.demo.entity.SysUser");
        ok("空 model addItem 后, Swing 确实自动选中了第 0 项 (这就是假象的来源)",
            raw.getSelectedIndex() == 0);
        ok("此时界面显示的值就是它",
            "com.demo.entity.SysUser".equals(ComboBoxValues.displayedText(raw)));

        section("2. 复现\"救不回来\": 重复选中同一个值不发事件");
        AtomicInteger events = new AtomicInteger();
        raw.addItemListener(event -> events.incrementAndGet());
        raw.setSelectedItem("com.demo.entity.SysUser");
        ok("重复选中当前项 -> 一个事件都不发 (所以用户再点也白点)",
            events.get() == 0);
        raw.setSelectedItem("com.demo.entity.Other");
        ok("换成另一项才发事件",
            events.get() > 0);

        section("3. 修复: 加载候选后必须还原成\"加载前的样子\"");
        JComboBox<String> combo = editableCombo();
        ComboBoxValues.loadCandidates(combo, Arrays.asList("AEntity", "BEntity", "CEntity"), "");
        ok("候选都进去了", combo.getItemCount() == 3);
        ok("加载前没选, 加载后也必须没选 (选中下标已清)",
            combo.getSelectedIndex() == -1);
        ok("而且界面也不该再显示某个类名",
            ComboBoxValues.displayedText(combo).isEmpty());

        section("4. 加载前已经有值 -> 必须保持住");
        JComboBox<String> kept = editableCombo();
        ComboBoxValues.selectOrAdd(kept, "BEntity");
        ComboBoxValues.loadCandidates(kept, Arrays.asList("AEntity", "CEntity"), "BEntity");
        ok("原来选的值还在", "BEntity".equals(ComboBoxValues.displayedText(kept)));
        ok("没有被自动选中项顶掉", kept.getSelectedIndex() >= 0);

        section("5. 用户输入优先于 model 选中项");
        JComboBox<String> typed = editableCombo();
        ComboBoxValues.selectOrAdd(typed, "AEntity");
        typed.getEditor().setItem("com.demo.entity.SysUser");
        ok("编辑器里刚敲的文本才是用户看到的",
            "com.demo.entity.SysUser".equals(ComboBoxValues.displayedText(typed)));

        section("6. selectOrAdd: 判重与追加");
        JComboBox<String> adding = editableCombo();
        ComboBoxValues.selectOrAdd(adding, "X");
        int afterFirst = adding.getItemCount();
        ComboBoxValues.selectOrAdd(adding, "X");
        ok("已存在的值不重复添加", adding.getItemCount() == afterFirst);
        ok("重复选中同一个值也是选中状态",
            "X".equals(ComboBoxValues.displayedText(adding)));
        ComboBoxValues.selectOrAdd(adding, "Y");
        ok("新的值会补进去", adding.getItemCount() == afterFirst + 1);

        section("7. loadCandidates 幂等 (重复加载不产生重复项)");
        JComboBox<String> twice = editableCombo();
        ComboBoxValues.loadCandidates(twice, Arrays.asList("AEntity", "BEntity"), "");
        ComboBoxValues.loadCandidates(twice, Arrays.asList("AEntity", "BEntity"), "");
        ok("刷两次还是 2 项", twice.getItemCount() == 2);

        section("8. 边界: 空候选 / null 参数不炸");
        JComboBox<String> emptyish = editableCombo();
        ComboBoxValues.loadCandidates(emptyish, Collections.emptyList(), "");
        ok("没有候选时也不留选中状态", emptyish.getSelectedIndex() == -1);
        ComboBoxValues.clearSelection(null);
        ComboBoxValues.loadCandidates(null, null, null);
        ok("null 输入不抛异常", true);
        ComboBoxValues.selectOrAdd(emptyish, "   ");
        ok("空白值不当作有效输入", ComboBoxValues.displayedText(emptyish).isEmpty());

        section("9. 场景重放: 对话框打开 -> 后台扫描回来 -> 用户选择");
        // 模拟 FieldPickerDialog 的真实时序
        JComboBox<String> dialog = editableCombo();
        // (1) 对话框刚打开, entity 还没定, combo 空
        ok("打开时界面为空", ComboBoxValues.displayedText(dialog).isEmpty());
        // (2) 后台扫描完成, 把候选刷进来 —— 修复前这一步会让界面"假装"选中第一项
        String keep = ComboBoxValues.displayedText(dialog);            // 空
        ComboBoxValues.loadCandidates(dialog,
            Arrays.asList("com.demo.entity.SysUser", "com.demo.entity.OrderEntity"), keep);
        ok("扫描完界面仍然是空的 (不再假装选中)",
            ComboBoxValues.displayedText(dialog).isEmpty());
        // (3) 用户从下拉里选中 SysUser
        dialog.setSelectedItem("com.demo.entity.SysUser");
        // (4) 不管事件有没有走到, 需要真值时都按"当前显示的值"拉一次 —— 这就是修复的核心
        String resolved = ComboBoxValues.displayedText(dialog);
        ok("拉取式同步能拿到用户选的类",
            "com.demo.entity.SysUser".equals(resolved));

        section("10. 对照: 修复前的写法会拿到错误的值");
        // 修复前: 加载完不还原选中状态, 内部读的又是 model.updateCombo 之后的东西
        JComboBox<String> broken = editableCombo();
        broken.addItem("com.demo.entity.OrderEntity");   // 相当于自动选中第一项
        broken.addItem("com.demo.entity.SysUser");
        ok("修复前界面会显示第一项 (用户以为选好了)",
            "com.demo.entity.OrderEntity".equals(ComboBoxValues.displayedText(broken)));
        ok("而用户其实想选的是 SysUser —— 界面的值≠用户意图, 所以现在必须清掉这个自动选中",
            !"com.demo.entity.SysUser".equals(ComboBoxValues.displayedText(broken)));

        section("11. 焦点监听该挂在哪 (挂错了兜底就永远不触发)");
        JComboBox<String> focusCombo = editableCombo();
        ok("编辑器组件是 JTextField (用户在它里面打字)",
            focusCombo.getEditor().getEditorComponent() instanceof JTextField);
        ok("它的 isFocusable 为 true, 也就是焦点真的落在它身上",
            focusCombo.getEditor().getEditorComponent().isFocusable());

        JTextField editorField = (JTextField) focusCombo.getEditor().getEditorComponent();
        ok("编辑器组件和组合框本身是两个不同组件 (事件源不同 -> 挂错就收不到)",
            !editorField.equals(focusCombo));
        int listenersBefore = editorField.getFocusListeners().length;
        editorField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
            }
        });
        ok("编辑器组件可挂载焦点监听器 (挂载点可达)",
            editorField.getFocusListeners().length == listenersBefore + 1);
        // headless 环境没有真实焦点系统, dispatchEvent 不会派发 FocusEvent, 所以这里
        // 不去伪造事件, 只把"该挂在哪"这个决定钉死 —— 结论是: 挂编辑器, 不能挂 combo。
        System.out.println("  [NOTE] 编辑器组件上原有 " + listenersBefore
            + " 个焦点监听器 (Swing UI 自己挂的), 焦点派发无法在 headless 下断言");

        System.out.println("PASS=" + passed + " FAIL=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static JComboBox<String> editableCombo() {
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        return combo;
    }

    private static void section(String title) {
        System.out.println("=== " + title + " ===");
    }

    private static void ok(String what, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + what);
        } else {
            failed++;
            System.out.println("  [FAIL] " + what);
        }
    }
}

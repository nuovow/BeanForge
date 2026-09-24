import com.nuo.beanforge.core.FieldModel;
import com.nuo.beanforge.core.TypeCatalog;
import com.nuo.beanforge.ui.FieldTableModel;
import com.nuo.beanforge.ui.TypeFilterCombo;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "类型列做校验 + 下拉框 + 字母匹配检索" 的离线验证。
 *
 * <p>两块分开测:</p>
 * <ul>
 *   <li>{@link TypeCatalog} —— 纯逻辑 (检索排序 / 合法性 / 规范化), 直接断言返回值</li>
 *   <li>{@link TypeFilterCombo} —— 用<b>真的 {@link JComboBox}</b>。因为这里最危险的
 *       不是过滤算法, 而是 Swing 自己的副作用: 重建 model 会顺手把用户刚敲的字
 *       换成第一项候选。这个只能拿真组件测。</li>
 * </ul>
 *
 * <p>headless 直接跑 (不弹下拉: {@code applyFilterAndShowPopup} 在无窗口系统下会跳过弹窗)。</p>
 */
public class TypeCatalogTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        filterTests();
        validateTests();
        normalizeTests();
        comboStateTests();
        comboFilteringTests();
        comboCommitTests();
        tableModelTests();

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过, " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ================================================================ 1. 字母检索

    private static void filterTests() {
        section("1. 字母匹配检索 (TypeCatalog.filter)");

        ok("输入为空 -> 返回候选表前 N 条",
            TypeCatalog.filter("", 3).equals(Arrays.asList("String", "Long", "Integer")));
        ok("输入为空 + 不限条数 -> 全部候选", TypeCatalog.filter("", 0).size() == 34);
        ok("limit 生效", TypeCatalog.filter("", 5).size() == 5);

        ok("str -> 第一条是 String", "String".equals(first(TypeCatalog.filter("str", 12))));
        ok("str -> String[] 紧随其后 (两者都是前缀命中)",
            TypeCatalog.filter("str", 12).get(1).equals("String[]"));
        ok("str -> 泛型里的 String 也算命中 (List<String>)",
            TypeCatalog.filter("str", 12).contains("List<String>"));

        ok("loc -> 只剩 LocalDateTime / LocalDate / LocalTime 三条",
            TypeCatalog.filter("loc", 12).equals(
                Arrays.asList("LocalDateTime", "LocalDate", "LocalTime")));

        // 这条是"词首命中"的价值: 大写 D 的开头也要能搜到
        ok("date -> Date 在前, LocalDateTime / LocalDate 也能搜到",
            TypeCatalog.filter("date", 12).equals(
                Arrays.asList("Date", "LocalDateTime", "LocalDate")));

        ok("map -> 前缀命中排前, 泛型参数里的 Map 排在后面",
            TypeCatalog.filter("map", 12).equals(Arrays.asList(
                "Map<String, Object>", "Map<String, String>", "Map<String, Long>",
                "List<Map<String, Object>>")));

        ok("obj -> Object 在前, 泛型里的 Object 也带出来",
            TypeCatalog.filter("obj", 12).equals(Arrays.asList(
                "Object", "Map<String, Object>", "List<Map<String, Object>>")));

        ok("大小写不敏感 (STRING -> String)",
            "String".equals(first(TypeCatalog.filter("STRING", 12))));

        List<String> longs = TypeCatalog.filter("long", 12);
        ok("long -> Long 在前", "Long".equals(first(longs)));
        ok("long -> 相关形态都带出来, 且不重复",
            longs.containsAll(Arrays.asList("Long", "Long[]", "List<Long>", "Set<Long>",
                "Map<String, Long>"))
                && noDuplicates(longs));

        ok("integer -> Integer / Integer[] / List<Integer>, 顺带 BigInteger",
            TypeCatalog.filter("integer", 12).equals(
                Arrays.asList("Integer", "Integer[]", "List<Integer>", "BigInteger")));

        // 只敲一个字母时最容易变噪音: 这里断言"只因含 b 而匹配"的 Double / Object 被挡掉了
        ok("单词首字母 (b) -> 只留 b 开头的, 不混入 Double / Object",
            TypeCatalog.filter("b", 12).equals(
                Arrays.asList("Boolean", "BigDecimal", "Byte", "BigInteger", "byte[]")));

        ok("完全相等时精确项排第一 (localdate -> LocalDate)",
            TypeCatalog.filter("localdate", 12).equals(
                Arrays.asList("LocalDate", "LocalDateTime")));

        ok("搜不到的输入 -> 空结果 (界面显示空列表, 但用户文本不会丢)",
            TypeCatalog.filter("zzzz", 12).isEmpty());
        ok("搜不到也不报错", TypeCatalog.filter(null, 12).size() == 12);
    }

    // ================================================================ 2. 合法性

    private static void validateTests() {
        section("2. 类型合法性校验 (TypeCatalog.validate)");

        String[] legal = {
            "String", "Long", "Integer", "BigDecimal", "LocalDateTime", "UUID",
            "int", "long", "boolean", "char", "double",
            "byte[]", "String[]", "Integer[]", "String[][]", "List<String>[]",
            "List<String>", "List<List<Integer>>", "Map<String, List<Long>>",
            "Map<String, Map<String, Long>>", "Optional<LocalDateTime>",
            "co.nuo.entity.SysUser", "java.util.List<Map<String, Object>>",
            "com.nuo.entity.SysUser[]",
            "List<?>", "List<? extends Number>", "List<? super Long>",
            "_Private", "$Proxy"
        };
        for (String type : legal) {
            ok("合法: " + type, TypeCatalog.validate(type) == null);
        }

        ok("空类型被拦下", "类型不能为空".equals(TypeCatalog.validate("")));
        ok("null 被拦下", "类型不能为空".equals(TypeCatalog.validate(null)));
        ok("纯空白被拦下", "类型不能为空".equals(TypeCatalog.validate("   ")));

        contains("List< (尖括号没闭合)", TypeCatalog.validate("List<"), "没有对应");
        contains("嵌套也没闭合", TypeCatalog.validate("Map<String, List<Long>"), "没有对应");
        contains("空泛型 <>", TypeCatalog.validate("List<>"), "至少要写一个类型");
        contains("多余的一个 >", TypeCatalog.validate("List<String>>"), "多余的内容");
        contains("逗号后面没类型", TypeCatalog.validate("Map<String,>"), "缺少类型");
        contains("逗号打头", TypeCatalog.validate("List<,String>"), "不能以");
        contains("分号混进来", TypeCatalog.validate("String;"), "多余的内容");
        contains("空格分隔两个名字", TypeCatalog.validate("a b"), "多余的内容");
        contains("数字开头", TypeCatalog.validate("123Abc"), "不能以");
        contains("数组后面跟着垃圾", TypeCatalog.validate("String[]x"), "多余的内容");
        contains("数组写了一半", TypeCatalog.validate("List[String]"), "[]");
        contains("泛型参数之间漏逗号", TypeCatalog.validate("List<St ring>"), "分隔");
        contains("关键字当类型", TypeCatalog.validate("class"), "关键字");
        contains("void 当类型", TypeCatalog.validate("void"), "关键字");
        contains("中文类型名", TypeCatalog.validate("中文字段"), "不能以");
        contains("含中文的泛型", TypeCatalog.validate("List<订单>"), "不能以");
    }

    // ================================================================ 3. 规范化

    private static void normalizeTests() {
        section("3. 规范化 (TypeCatalog.normalize)");

        eq("去掉首尾空白", TypeCatalog.normalize("  String  "), "String");
        eq("泛型里多余空格压掉",
            TypeCatalog.normalize("Map< String , List< Long > >"), "Map<String, List<Long>>");
        eq("尖括号前空格", TypeCatalog.normalize("List < String >"), "List<String>");
        eq("逗号后统一加空格", TypeCatalog.normalize("Map<String,Object>"), "Map<String, Object>");
        eq("数组也收拾", TypeCatalog.normalize("String [ ]"), "String[]");
        eq("int  + 数组", TypeCatalog.normalize("int  []"), "int[]");
        eq("null -> 空串", TypeCatalog.normalize(null), "");
        eq("空白 -> 空串", TypeCatalog.normalize("   "), "");
        // 敲到一半的非法输入也要原样留着, 否则编辑框里的字会"被吃掉"
        eq("非法输入原样返回", TypeCatalog.normalize("List<"), "List<");
        eq("中文原样返回", TypeCatalog.normalize("中文字段"), "中文字段");

        eq("合法才规范化", TypeCatalog.normalizeIfValid("List< String >"), "List<String>");
        eq("非法不规范化", TypeCatalog.normalizeIfValid("List<"), "List<");
    }

    // ================================================================ 4. 下拉框初始状态

    private static void comboStateTests() {
        section("4. 类型下拉框: 初始状态 (真 JComboBox)");

        JComboBox<String> combo = TypeFilterCombo.create();
        ok("可编辑 (能直接手打类型)", combo.isEditable());
        ok("候选装载完整: " + combo.getItemCount() + " 条",
            combo.getItemCount() == TypeCatalog.suggestions().size());
        ok("建好之后必须是\"没选中\" —— 不能像裸 Swing 那样自动选中第 0 项",
            combo.getSelectedIndex() == -1);
        ok("界面上也不该显示某个类型", TypeFilterCombo.commitText(combo).isEmpty());

        // 复现裸 Swing 的行为做对照: 这就是要防的那个副作用
        JComboBox<String> raw = new JComboBox<>();
        raw.setEditable(true);
        raw.addItem("String");
        ok("[对照] 裸 JComboBox 加第一项会自动选中第 0 项 (所以要显式清掉)",
            raw.getSelectedIndex() == 0);

        JComboBox<String> reloaded = new JComboBox<>();
        reloaded.setEditable(true);
        TypeFilterCombo.reload(reloaded, Arrays.asList("A", "B"));
        ok("reload 装完候选后也不自动选中",
            reloaded.getSelectedIndex() == -1 && reloaded.getItemCount() == 2);
    }

    // ================================================================ 5. 边打字边过滤

    private static void comboFilteringTests() {
        section("5. 类型下拉框: 边打字边过滤 (文本不能被冲掉)");

        JComboBox<String> combo = TypeFilterCombo.create();

        type(combo, "l");
        ok("敲 l -> 前缀命中 9 条 + 词首命中 2 条 (Set<Long> / Map<String, Long>)",
            combo.getItemCount() == 11);
        ok("敲 l -> 第一项是 Long", "Long".equals(combo.getItemAt(0)));
        eq("敲 l 之后编辑框里还是 l (没被第一项候选顶掉)", editorText(combo), "l");
        ok("敲 l 之后仍是\"没选中\"状态", combo.getSelectedIndex() == -1);

        type(combo, "lo");
        eq("继续敲 o -> 文本仍是 lo", editorText(combo), "lo");
        ok("继续敲 o -> 候选再收窄到 8 条", combo.getItemCount() == 8);
        ok("继续敲 o -> 前五条都是 lo 开头的",
            combo.getItemAt(0).equals("Long") && combo.getItemAt(1).equals("LocalDateTime")
                && combo.getItemAt(2).equals("LocalDate") && combo.getItemAt(3).equals("LocalTime")
                && combo.getItemAt(4).equals("Long[]"));

        type(combo, "loc");
        ok("敲 loc -> 只剩 3 条", combo.getItemCount() == 3);
        ok("敲 loc -> 顺序是 LocalDateTime / LocalDate / LocalTime",
            combo.getItemAt(0).equals("LocalDateTime")
                && combo.getItemAt(1).equals("LocalDate")
                && combo.getItemAt(2).equals("LocalTime"));
        eq("文本原样保留", editorText(combo), "loc");

        type(combo, "local");
        ok("敲到 local -> 三条都还在 (前缀命中)", combo.getItemCount() == 3);

        type(combo, "localdate");
        ok("敲到 localdate -> 精确项排第一",
            combo.getItemAt(0).equals("LocalDate") && combo.getItemCount() == 2);

        // 退格方向也要重新算: 过滤不能是"只进不退"的累积状态
        type(combo, "local");
        ok("退格回 local -> 候选项回来了, 不是累积过滤",
            combo.getItemCount() == 3 && combo.getItemAt(0).equals("LocalDateTime"));

        JComboBox<String> two = TypeFilterCombo.create();
        type(two, "List<");
        ok("敲 List< 仍能给出 List<...> 候选 (边敲边搜不打岔)",
            two.getItemCount() == 4 && two.getItemAt(0).equals("List<String>"));
        ok("同时它此刻是非法类型 (还没敲完), 但文本保留着",
            TypeFilterCombo.errorOf(two) != null && editorText(two).equals("List<"));

        JComboBox<String> mine = TypeFilterCombo.create();
        type(mine, "MyCustomType");
        ok("候选表里没有的类型 -> 下拉为空", mine.getItemCount() == 0);
        eq("但用户敲的文本不能被清掉 (这是本次改动的关键)", editorText(mine), "MyCustomType");
        ok("自定义类型自身是合法的, 不报错", TypeFilterCombo.errorOf(mine) == null);

        JComboBox<String> exact = TypeFilterCombo.create();
        type(exact, "String");
        ok("敲完 String -> 候选都是含 String 的 (10 条), 第一条是 String",
            exact.getItemCount() == 10 && exact.getItemAt(0).equals("String"));
        eq("文本仍是 String", editorText(exact), "String");

        ok("过滤完成后重入闸门必须复位 (否则后续打字不再过滤)",
            !TypeFilterCombo.isFiltering(exact));
        // 闸门没复位就会卡在"字打得进去但候选不再更新", 用新输入验一次
        type(exact, "Big");
        ok("闸门复位后还能继续过滤", exact.getItemCount() == 2);
    }

    // ================================================================ 6. 取值

    private static void comboCommitTests() {
        section("6. 类型下拉框: 提交时取什么值");

        JComboBox<String> combo = TypeFilterCombo.create();
        ok("空框提交 -> 空串 (界面据此报\"类型不能为空\")",
            TypeFilterCombo.commitText(combo).isEmpty());

        type(combo, "List< String >");
        eq("提交时规范化", TypeFilterCombo.commitText(combo), "List<String>");
        ok("规范化结果合法", TypeFilterCombo.errorOf(combo) == null);

        // 这是 DefaultCellEditor 的经典坑: 它默认返回 combo.getSelectedItem(),
        // 而手打的类型不在候选里 -> 选中项是 null -> 单元格被写成空
        JComboBox<String> typed = TypeFilterCombo.create();
        type(typed, "MyType");
        ok("[对照] 手打未命中候选时, 选中项确实是 null",
            typed.getSelectedIndex() == -1 && typed.getSelectedItem() == null);
        eq("所以取值必须走编辑器文本 (TypeCellEditor 重写了 getCellEditorValue)",
            TypeFilterCombo.commitText(typed), "MyType");

        type(typed, "List<");
        ok("非法类型能被 errorOf 报出来", TypeFilterCombo.errorOf(typed) != null);
        contains("错误原因具体到哪一层没闭合", TypeFilterCombo.errorOf(typed), "没有对应");

        // 选中候选 (模拟用户从下拉里点一条) 后, 提交值就是它
        JComboBox<String> picked = TypeFilterCombo.create();
        picked.setSelectedItem("Map<String, Long>");
        eq("从下拉里选中 -> 提交选中值", TypeFilterCombo.commitText(picked), "Map<String, Long>");
    }

    // ================================================================ 7. 表格模型

    /**
     * "类型列做校验" 落到界面上的样子: 表格从模型里读什么、生成前拦什么。
     *
     * <p>{@link FieldTableModel} 只依赖 core + Swing, 所以这一段也能离线断言
     * (只有"标红"那一步要 IDEA 的颜色, 由构建期编译覆盖)。</p>
     */
    private static void tableModelTests() {
        section("7. 表格模型: 校验结果怎么被界面读到");

        List<FieldModel> fields = new ArrayList<>();
        fields.add(field("userName", "String", true));
        fields.add(field("amount", "BigDecimal", true));
        FieldTableModel model = new FieldTableModel(fields);

        ok("正常字段没有类型错误",
            model.typeErrorAt(0) == null && model.typeErrorAt(1) == null);
        ok("全部合法 -> 生成前的拦截列表为空", model.selectedTypeErrors().isEmpty());

        model.setValueAt("List< String >", 0, FieldTableModel.COL_TYPE);
        eq("在表格里编辑类型时会顺手规范化",
            String.valueOf(model.getValueAt(0, FieldTableModel.COL_TYPE)), "List<String>");

        model.setValueAt("List<", 1, FieldTableModel.COL_TYPE);
        contains("非法类型 -> 该行报错 (渲染器据此把格子标红)", model.typeErrorAt(1), "没有对应");
        ok("合法的那行不受影响", model.typeErrorAt(0) == null);

        model.getFieldAt(1).setSelected(false);
        ok("没勾的字段不算出错 (它本来就不会生成)", model.selectedTypeErrors().isEmpty());
        model.getFieldAt(1).setSelected(true);

        int emptyRow = model.addEmptyField();
        contains("手工加的字段还没填类型 -> 报\"类型不能为空\"",
            model.typeErrorAt(emptyRow), "不能为空");

        List<String> errors = model.selectedTypeErrors();
        ok("两个有问题的字段都被拦下", errors.size() == 2);
        contains("拦截文案带字段名", errors.get(0), "amount");
        contains("未命名的字段也有可读提示", errors.get(1), "未命名字段");
    }

    private static FieldModel field(String name, String type, boolean selected) {
        FieldModel model = new FieldModel();
        model.setName(name);
        model.setTypeText(type);
        model.setSelected(selected);
        return model;
    }

    // ================================================================ 小工具

    /**
     * 模拟"用户在编辑框里敲出 text" —— 走真 JTextField.setText, 再走界面同样的入口。
     *
     * <p>用的是 {@code applyFilterAndShowPopup} 而不是只过滤: 顺带验证 headless 下
     * 弹窗那一步不会抛异常 (真实环境里它要弹下拉, 无窗口系统时得安静跳过)。</p>
     */
    private static void type(JComboBox<String> combo, String text) {
        JTextField field = (JTextField) combo.getEditor().getEditorComponent();
        field.setText(text);
        TypeFilterCombo.applyFilterAndShowPopup(combo);
    }

    /**
     * 编辑框里的原始文本 (未经规范化, 也不看选中项)。
     */
    private static String editorText(JComboBox<String> combo) {
        return ((JTextField) combo.getEditor().getEditorComponent()).getText();
    }

    private static String first(List<String> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private static boolean noDuplicates(List<String> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(i).equals(list.get(j))) {
                    return false;
                }
            }
        }
        return true;
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

    private static void contains(String what, String actual, String fragment) {
        if (actual != null && actual.contains(fragment)) {
            passed++;
            System.out.println("  [OK]   " + what + " -> \"" + actual + "\"");
        } else {
            failed++;
            System.out.println("  [FAIL] " + what + " -> 期望包含 \"" + fragment + "\", 实际 \"" + actual + "\"");
        }
    }
}

package com.nuo.beanforge.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.ui.DocumentAdapter;
import com.nuo.beanforge.core.DtoVoGenerator;
import com.nuo.beanforge.core.EntityModel;
import com.nuo.beanforge.core.GenerateOptions;
import com.nuo.beanforge.core.Naming;
import com.nuo.beanforge.psi.EntityExtractor;
import com.nuo.beanforge.psi.EntityLocator;
import com.nuo.beanforge.psi.GeneratedWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * "从实体生成 DTO/VO" 的主对话框: 选实体 -> 筛字段 -> 定选项。
 */
public class FieldPickerDialog extends DialogWrapper {

    private static final int MAX_SCAN = 500;

    private final Project project;
    private final String initialClassName;

    private PsiClass entity;
    private EntityModel model;
    private FieldTableModel tableModel;

    private JComboBox<String> entityCombo;
    private JComboBox<String> kindCombo;
    private JTextField packageField;
    private JTextField classField;
    private JTextField classCommentField;
    private JTable table;
    private JLabel countLabel;

    private JCheckBox removePersistenceBox;
    private JCheckBox keepCommentsBox;
    private JCheckBox includeSuperBox;
    private JCheckBox dataBox;
    private JCheckBox getterSetterBox;
    private JCheckBox noArgsBox;
    private JCheckBox allArgsBox;
    private JCheckBox builderBox;
    private JCheckBox accessorsBox;
    private JCheckBox serializableBox;

    /**
     * 首次推断类名时用调用方给的类名 (例如从 {@code Result<LoginVo>} 进来的 "LoginVo"),
     * 用过一次之后改由模板规则推断, 否则用户切换 DTO/VO 时类名不会跟着变。
     */
    private boolean initialClassNameConsumed = false;
    /**
     * 程序修改下拉框时不要触发"切换实体"。
     */
    private boolean updatingCombo = false;

    public FieldPickerDialog(@NotNull Project project,
                             @Nullable PsiClass entity,
                             @Nullable String initialClassName) {
        super(project, true);
        this.project = project;
        this.initialClassName = initialClassName;
        if (entity != null) {
            applyEntity(entity);
        } else {
            // 从"创建一个还不存在的类"进来的场景: 实体留给用户在下拉里选
            this.model = new EntityModel();
            this.model.setFields(new ArrayList<>());
        }
        setTitle("BeanForge - 生成 DTO/VO");
        setResizable(true);
        init();
    }

    @Nullable
    public PsiClass getEntity() {
        return entity;
    }

    /**
     * 带上用户在界面上做的修改 (勾选、改名、改注释、手加字段)。
     */
    @NotNull
    public EntityModel getEntityModel() {
        return model;
    }

    /**
     * 收集界面上的生成选项。
     */
    @NotNull
    public GenerateOptions buildOptions() {
        GenerateOptions options = new GenerateOptions();
        options.setTargetPackage(text(packageField));
        options.setTargetClassName(text(classField));
        options.setClassComment(text(classCommentField));
        options.setRemovePersistenceAnnotations(removePersistenceBox.isSelected());
        options.setKeepComments(keepCommentsBox.isSelected());
        options.setIncludeSuperFields(includeSuperBox.isSelected());
        options.setLombokData(dataBox.isSelected());
        options.setLombokGetterSetter(getterSetterBox.isSelected());
        options.setLombokNoArgsConstructor(noArgsBox.isSelected());
        options.setLombokAllArgsConstructor(allArgsBox.isSelected());
        options.setLombokBuilder(builderBox.isSelected());
        options.setLombokAccessorsChain(accessorsBox.isSelected());
        options.setSerializable(serializableBox.isSelected());
        return options;
    }

    // ------------------------------------------------------------------ 实体装载

    private void applyEntity(@NotNull PsiClass psiClass) {
        boolean includeSuper = includeSuperBox != null && includeSuperBox.isSelected();
        EntityModel extracted = EntityExtractor.extract(psiClass, includeSuper);
        this.entity = psiClass;
        this.model = extracted;
        if (tableModel != null) {
            // 让表格和模型共用同一个 list, 界面改动即时反映到生成结果
            tableModel.getFields().clear();
            tableModel.getFields().addAll(extracted.getFields());
            model.setFields(tableModel.getFields());
            tableModel.fireTableDataChanged();
        }
        if (classCommentField != null) {
            classCommentField.setText(String.join("\n", extracted.getClassComments()));
        }
        applyKindSuggestion();
        updateCountLabel();
        if (entityCombo != null) {
            selectComboText(entityCombo, extracted.getQualifiedName());
        }
        initValidation();
    }

    private void applyKindSuggestion() {
        if (packageField == null || classField == null) {
            return;
        }
        String kind = currentKind();
        packageField.setText(Naming.guessTargetPackage(model.getPackageName(), kind));
        if (!initialClassNameConsumed && initialClassName != null && !initialClassName.trim().isEmpty()) {
            classField.setText(initialClassName.trim());
            initialClassNameConsumed = true;
        } else {
            classField.setText(Naming.guessClassName(model.getClassName(), kind));
        }
    }

    private String currentKind() {
        Object value = kindCombo == null ? null : kindCombo.getSelectedItem();
        return value == null ? "dto" : value.toString();
    }

    private String initialKind() {
        if (initialClassName != null) {
            String upper = initialClassName.trim().toUpperCase();
            if (upper.endsWith("VO")) {
                return "vo";
            }
            if (upper.endsWith("DTO")) {
                return "dto";
            }
        }
        return "dto";
    }

    /**
     * 按名字在下拉里选中某一项 (不存在就补进去), 期间不触发切换逻辑。
     */
    private void selectComboText(@NotNull JComboBox<String> combo, String value) {
        if (value == null) {
            return;
        }
        // 保存/恢复而不是直接置 false: 这个方法会被嵌在另一段 updatingCombo=true 的逻辑里调用
        boolean previous = updatingCombo;
        updatingCombo = true;
        try {
            ComboBoxValues.selectOrAdd(combo, value);
        } finally {
            updatingCombo = previous;
        }
    }

    /**
     * 把组合框当前显示的值当作真值同步进来 —— <b>拉取式</b>, 而不是等事件推过来。
     *
     * <p>为什么必须这么写: 可编辑 {@link JComboBox} 往空的 model 里 {@code addItem} 时,
     * 会把第一项<b>自动选中</b>。候选是后台扫完再刷进下拉的, 那一瞬间 {@code updatingCombo}
     * 是 true, 事件被有意吞掉 —— 于是出现最迷惑的现象: <b>界面上明明显示着一个类名,
     * 内部却还是"没选"</b>, 点确定报"没有选中实体类"; 用户再去点那一项也救不回来, 因为
     * JComboBox 对"重复选中同一个值"不发事件。所以这里不再依赖事件, 改为每次需要真值时
     * 主动按当前显示文本解析一次。</p>
     */
    private void syncEntityFromCombo() {
        if (updatingCombo || entityCombo == null) {
            return;
        }
        String qualifiedName = ComboBoxValues.displayedText(entityCombo);
        if (qualifiedName.isEmpty()) {
            return;
        }
        if (entity != null && qualifiedName.equals(entity.getQualifiedName())) {
            return;
        }
        PsiClass found = ReadAction.compute(() -> resolveEntityByText(qualifiedName));
        if (found == null) {
            setErrorText("找不到类: " + qualifiedName + " (可以直接输入全限定名)");
            return;
        }
        setErrorText(null);
        applyEntity(found);
    }

    /**
     * 先按全限定名找; 只写了简单名 (没有点号) 时再按简单名兜底找一遍,
     * 这样"输 SysUser 回车"也能用, 不必逼用户敲全限定名。
     */
    @Nullable
    private PsiClass resolveEntityByText(@NotNull String name) {
        PsiClass byQualifiedName = EntityExtractor.findClass(project, name);
        if (byQualifiedName != null) {
            return byQualifiedName;
        }
        if (name.indexOf('.') >= 0) {
            return null;
        }
        List<PsiClass> entities = EntityLocator.entitiesByShortName(project, name);
        if (!entities.isEmpty()) {
            return entities.get(0);
        }
        List<PsiClass> any = EntityLocator.byShortName(project, name);
        return any.isEmpty() ? null : any.get(0);
    }

    /**
     * 后台扫描项目里的实体候选, 扫完再更新下拉 (不卡界面)。
     */
    private void loadCandidatesAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<String> names = ReadAction.compute(() -> {
                List<String> collected = new ArrayList<>();
                for (PsiClass psiClass : EntityLocator.scanEntities(project, MAX_SCAN)) {
                    String qualifiedName = psiClass.getQualifiedName();
                    if (qualifiedName != null) {
                        collected.add(qualifiedName);
                    }
                }
                return collected;
            });
            SwingUtilities.invokeLater(() -> {
                if (project.isDisposed() || entityCombo == null) {
                    return;
                }
                // 拉一份"加载前的样子", 刷完之后还要一模一样地还回去
                String keep = ComboBoxValues.displayedText(entityCombo);
                boolean previous = updatingCombo;
                updatingCombo = true;
                try {
                    ComboBoxValues.loadCandidates(entityCombo, names, keep);
                } finally {
                    updatingCombo = previous;
                }
            });
        });
    }

    // ------------------------------------------------------------------ 界面组装

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildFieldPanel(), BorderLayout.CENTER);
        root.add(buildOptionPanel(), BorderLayout.SOUTH);
        // 实体还没定的时候, 类名先按调用方给的占上 (例如 Result<LoginVo> 里的 LoginVo)
        if (initialClassName != null && !initialClassName.trim().isEmpty()
            && text(classField).isEmpty()) {
            classField.setText(initialClassName.trim());
        }
        loadCandidatesAsync();
        return root;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return classField;
    }

    @Nullable
    @Override
    protected String getDimensionServiceKey() {
        return "com.nuo.beanforge.FieldPickerDialog";
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("生成目标"));

        entityCombo = new JComboBox<>();
        entityCombo.setEditable(true);
        entityCombo.setToolTipText("选一个实体类; 也可以直接输入全限定名后回车");
        selectComboText(entityCombo, model.getQualifiedName());
        // 三条触发路径都指向同一个"拉取式"同步方法。单靠事件一定会漏:
        //   - ItemListener: 从下拉列表里改选另一项
        //   - ActionListener: 在编辑器里回车 (可编辑组合框只在回车/选中时才发 action,
        //     不是每次敲键, 所以不会"打字打一半就去解析类名")
        //   - FocusListener: 输完直接按 Tab 或点别处, 没回车也不会漏
        // 另外 doOKAction 里还有最后一道防线。
        entityCombo.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                syncEntityFromCombo();
            }
        });
        if (entityCombo.getEditor().getEditorComponent() instanceof JTextField) {
            JTextField editorField = (JTextField) entityCombo.getEditor().getEditorComponent();
            editorField.addActionListener(event -> syncEntityFromCombo());
            // 焦点监听必须挂在编辑器上, 不能挂在 combo 上: setEditable(true) 会把 JComboBox
            // 自己的 focusable 置成 false, 真正获焦的是内部那个 JTextField。
            editorField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent event) {
                    syncEntityFromCombo();
                }
            });
        }

        kindCombo = new JComboBox<>(new String[]{"dto", "vo"});
        kindCombo.setSelectedItem(initialKind());
        kindCombo.addActionListener(event -> applyKindSuggestion());

        packageField = new JTextField();
        classField = new JTextField();
        classCommentField = new JTextField();
        classCommentField.setToolTipText("留空则不生成类注释");
        addValidationTrigger(packageField);
        addValidationTrigger(classField);

        int row = 0;
        addRow(panel, row++, "实体类", entityCombo, 1.0);
        addRow(panel, row++, "生成类型", kindCombo, 0.0);
        addRow(panel, row++, "目标包", packageField, 1.0);
        addRow(panel, row++, "类 名", classField, 1.0);
        addRow(panel, row, "类注释", classCommentField, 1.0);
        return panel;
    }

    private void addRow(JPanel panel, int row, String label, JComponent field, double weightX) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(2, 4, 2, 8);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = weightX;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(2, 0, 2, 4);
        panel.add(field, fieldConstraints);
    }

    private JPanel buildFieldPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createTitledBorder("保留哪些字段"));

        tableModel = new FieldTableModel(model.getFields());
        model.setFields(tableModel.getFields());
        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);

        TableColumnModel columns = table.getColumnModel();
        setColumnWidth(columns.getColumn(FieldTableModel.COL_SELECTED), 52, 52, 52);
        setColumnWidth(columns.getColumn(FieldTableModel.COL_TYPE), 170, 120, 400);
        setColumnWidth(columns.getColumn(FieldTableModel.COL_NAME), 160, 120, 400);
        setColumnWidth(columns.getColumn(FieldTableModel.COL_COMMENT), 340, 200, 900);
        // 复选框列用 checkbox 编辑器, 单击即切换
        columns.getColumn(FieldTableModel.COL_SELECTED)
            .setCellEditor(new DefaultCellEditor(new JCheckBox()));
        // 类型列: 可编辑下拉 + 敲字母检索候选 + 提交前校验 (非法类型不让离开单元格)
        TableColumn typeColumn = columns.getColumn(FieldTableModel.COL_TYPE);
        typeColumn.setCellEditor(new TypeCellEditor());
        typeColumn.setCellRenderer(new TypeErrorRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(780, 300));
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buildFieldToolbar(), BorderLayout.SOUTH);

        updateCountLabel();
        return panel;
    }

    private JComponent buildFieldToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

        JButton selectAll = new JButton("全选");
        selectAll.addActionListener(event -> {
            tableModel.selectAll(true);
            updateCountLabel();
        });
        JButton selectNone = new JButton("全不选");
        selectNone.addActionListener(event -> {
            tableModel.selectAll(false);
            updateCountLabel();
        });
        JButton add = new JButton("添加字段");
        add.addActionListener(event -> {
            int row = tableModel.addEmptyField();
            table.setRowSelectionInterval(row, row);
            table.editCellAt(row, FieldTableModel.COL_NAME);
            updateCountLabel();
        });
        JButton remove = new JButton("删除");
        remove.addActionListener(event -> {
            int[] rows = table.getSelectedRows();
            // 从后往前删, 否则前面的行号会失效
            for (int i = rows.length - 1; i >= 0; i--) {
                tableModel.removeField(table.convertRowIndexToModel(rows[i]));
            }
            updateCountLabel();
        });
        JButton up = new JButton("上移");
        up.addActionListener(event -> moveSelected(-1));
        JButton down = new JButton("下移");
        down.addActionListener(event -> moveSelected(1));

        countLabel = new JLabel();
        bar.add(selectAll);
        bar.add(selectNone);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(add);
        bar.add(remove);
        bar.add(up);
        bar.add(down);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(countLabel);
        return bar;
    }

    private void moveSelected(int delta) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        int moved = tableModel.moveField(table.convertRowIndexToModel(row), delta);
        if (moved != row) {
            table.setRowSelectionInterval(moved, moved);
        }
    }

    private JPanel buildOptionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("生成选项"));

        dataBox = new JCheckBox("@Data", true);
        getterSetterBox = new JCheckBox("@Getter/@Setter", false);
        noArgsBox = new JCheckBox("@NoArgsConstructor", true);
        allArgsBox = new JCheckBox("@AllArgsConstructor", true);
        builderBox = new JCheckBox("@Builder", false);
        accessorsBox = new JCheckBox("@Accessors(chain = true)", false);
        serializableBox = new JCheckBox("implements Serializable", false);

        // @Data 已经带了 getter/setter, 两个都勾没有意义, 所以互斥
        dataBox.addItemListener(event -> {
            if (dataBox.isSelected()) {
                getterSetterBox.setSelected(false);
            }
        });
        getterSetterBox.addItemListener(event -> {
            if (getterSetterBox.isSelected()) {
                dataBox.setSelected(false);
            }
        });

        JPanel lombokRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        lombokRow.add(new JLabel("Lombok:"));
        lombokRow.add(dataBox);
        lombokRow.add(getterSetterBox);
        lombokRow.add(noArgsBox);
        lombokRow.add(allArgsBox);
        lombokRow.add(builderBox);
        lombokRow.add(accessorsBox);

        JPanel behaviorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        removePersistenceBox = new JCheckBox("去掉 MyBatis / JPA 注解", true);
        keepCommentsBox = new JCheckBox("保留字段注释", true);
        includeSuperBox = new JCheckBox("包含父类字段", false);
        includeSuperBox.setToolTipText("勾选后会把继承来的字段一并生成");
        includeSuperBox.addItemListener(event -> {
            if (entity != null) {
                applyEntity(entity);
            }
        });
        behaviorRow.add(removePersistenceBox);
        behaviorRow.add(keepCommentsBox);
        behaviorRow.add(includeSuperBox);
        behaviorRow.add(serializableBox);

        JPanel hintRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        hintRow.add(new JLabel("双击单元格可改类型 / 字段名 / 注释; 类型列是个下拉, 输入字母就能检索 (如 loc -> LocalDateTime)"));

        panel.add(lombokRow);
        panel.add(behaviorRow);
        panel.add(hintRow);
        return panel;
    }

    private void updateCountLabel() {
        if (countLabel == null || tableModel == null) {
            return;
        }
        countLabel.setText("已选 " + tableModel.selectedCount() + " / " + tableModel.getRowCount() + " 个字段");
    }

    private static void setColumnWidth(TableColumn column, int width, int min, int max) {
        column.setPreferredWidth(width);
        column.setMinWidth(min);
        column.setMaxWidth(max);
    }

    /**
     * 类型列的渲染器: 非法类型染红, 悬停给出具体原因。
     *
     * <p>这样做是因为"提交时拒绝"只挡住了结束编辑, 用户看不出哪一行有问题;
     * 表格里的红色是<b>随时可见</b>的, 一眼就知道该修哪个格子。</p>
     */
    private class TypeErrorRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                source, value, isSelected, hasFocus, row, column);
            if (tableModel == null) {
                return component;
            }
            String error = tableModel.typeErrorAt(source.convertRowIndexToModel(row));
            if (error == null) {
                component.setForeground(isSelected ? source.getSelectionForeground() : source.getForeground());
                setToolTipText("可输入字母检索候选 (如 loc -> LocalDateTime), 也支持手写 List<String> / Map<String, Long>");
            } else {
                component.setForeground(UiColors.ERROR);
                setToolTipText("类型不合法: " + error);
            }
            return component;
        }
    }

    private void addValidationTrigger(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                initValidation();
            }
        });
    }

    private static String text(JTextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    // ------------------------------------------------------------------ 校验

    /**
     * 点"确定"前再同步一次: 用户可能输入类名后直接点确定 (没回车、也没失焦),
     * 或者组合框的选中项因故没走到事件。这是"明明选了却报没选"的最后一道防线。
     */
    @Override
    protected void doOKAction() {
        syncEntityFromCombo();
        super.doOKAction();
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (entity == null || model == null) {
            return new ValidationInfo("没有选中实体类: 在\"实体类\"下拉里选一个, 或输入全限定名后回车", entityCombo);
        }
        String packageName = text(packageField);
        if (packageName.isEmpty()) {
            return new ValidationInfo("目标包不能为空", packageField);
        }
        if (!isValidPackage(packageName)) {
            return new ValidationInfo("包名不合法: " + packageName, packageField);
        }
        String className = text(classField);
        if (className.isEmpty()) {
            return new ValidationInfo("类名不能为空", classField);
        }
        if (!DtoVoGenerator.isValidJavaIdentifier(className)) {
            return new ValidationInfo("类名不合法 (不能是关键字, 也不能带 . 或空格): " + className, classField);
        }
        if (tableModel != null && tableModel.selectedCount() == 0) {
            return new ValidationInfo("至少要保留一个字段", table);
        }
        if (tableModel != null) {
            List<String> typeErrors = tableModel.selectedTypeErrors();
            if (!typeErrors.isEmpty()) {
                String more = typeErrors.size() > 1 ? " (还有 " + (typeErrors.size() - 1) + " 个)" : "";
                return new ValidationInfo("字段类型不合法 -> " + typeErrors.get(0) + more
                    + "。类型列里红色的就是有问题的格子", table);
            }
        }
        VirtualFile existing = ReadAction.compute(
            () -> GeneratedWriter.findExisting(project, entity, packageName, className));
        if (existing != null) {
            return new ValidationInfo("目标类已存在, 不会覆盖: " + existing.getPath(), classField);
        }
        return null;
    }

    /**
     * 包名: 每段都必须是合法标识符。
     */
    static boolean isValidPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        for (String segment : packageName.split("\\.", -1)) {
            if (segment.isEmpty() || !DtoVoGenerator.isValidJavaIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }
}

package com.nuo.beanforge.ui;

import com.nuo.beanforge.core.FieldModel;
import com.nuo.beanforge.core.TypeCatalog;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * 字段表格的数据源。
 *
 * <p>直接读写 {@link FieldModel}, 所以"表格里的内容 == 要生成的内容", 中间没有第二份状态。</p>
 *
 * <p>注释列显示时把多行拼成一行 (表格单元格放不下多行), 但只有用户真的编辑了该列,
 * 才会把模型里的多行注释覆盖成一行 —— 没动过的字段会原样保留多行注释。</p>
 */
public class FieldTableModel extends AbstractTableModel {

    public static final int COL_SELECTED = 0;
    public static final int COL_TYPE = 1;
    public static final int COL_NAME = 2;
    public static final int COL_COMMENT = 3;

    private static final String[] COLUMNS = {"保留", "类型", "字段名", "注释"};

    private final List<FieldModel> fields;

    public FieldTableModel(List<FieldModel> fields) {
        this.fields = fields == null ? new ArrayList<>() : fields;
    }

    public List<FieldModel> getFields() {
        return fields;
    }

    public FieldModel getFieldAt(int row) {
        if (row < 0 || row >= fields.size()) {
            return null;
        }
        return fields.get(row);
    }

    @Override
    public int getRowCount() {
        return fields.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == COL_SELECTED ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FieldModel field = fields.get(rowIndex);
        switch (columnIndex) {
            case COL_SELECTED:
                return field.isSelected();
            case COL_TYPE:
                return field.getTypeText() == null ? "" : field.getTypeText();
            case COL_NAME:
                return field.getName() == null ? "" : field.getName();
            case COL_COMMENT:
                return field.commentText();
            default:
                return "";
        }
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        FieldModel field = fields.get(rowIndex);
        switch (columnIndex) {
            case COL_SELECTED:
                field.setSelected(Boolean.TRUE.equals(value));
                break;
            case COL_TYPE:
                // 规范化 (压掉多余空白、逗号后补空格); 非法输入也照收 —— 用户敲到一半的
                // "List<" 不该被静默吃掉, 合法性由 typeErrorAt 单独报告, 表格据此标红
                field.setTypeText(TypeCatalog.normalize(stringOf(value)));
                break;
            case COL_NAME:
                field.setName(stringOf(value));
                break;
            case COL_COMMENT:
                field.setComments(splitComment(stringOf(value)));
                break;
            default:
                break;
        }
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    /**
     * 该行类型文本的校验结果。
     *
     * @return {@code null} 表示合法; 否则返回错误说明 (表格据此把类型列染红)
     */
    public String typeErrorAt(int row) {
        FieldModel field = getFieldAt(row);
        return field == null ? null : TypeCatalog.validate(field.getTypeText());
    }

    /**
     * 选中的字段里类型非法的那些, 形如 {@code "userName: 类型不合法 (泛型 \"<\" 没有对应的 \">\")"}。
     *
     * <p>只报选中的: 没勾的字段本来就不会生成, 拦下来只会让人以为"哪儿都动不了"。</p>
     */
    public List<String> selectedTypeErrors() {
        List<String> errors = new ArrayList<>();
        for (FieldModel field : fields) {
            if (!field.isSelected()) {
                continue;
            }
            String error = TypeCatalog.validate(field.getTypeText());
            if (error != null) {
                String name = field.getName() == null || field.getName().trim().isEmpty()
                    ? "(未命名字段)" : field.getName().trim();
                errors.add(name + ": " + error);
            }
        }
        return errors;
    }

    /**
     * 新增一个空字段并把行的内容交给界面去编辑。
     */
    public int addEmptyField() {
        FieldModel field = new FieldModel();
        field.setTypeText("");
        field.setName("");
        field.setSelected(true);
        field.setManual(true);
        fields.add(field);
        int row = fields.size() - 1;
        fireTableRowsInserted(row, row);
        return row;
    }

    public void removeField(int row) {
        if (row < 0 || row >= fields.size()) {
            return;
        }
        fields.remove(row);
        fireTableRowsDeleted(row, row);
    }

    /**
     * 上下移动一行。
     *
     * @return 移动后的行号 (已经在边界上则返回原行号)
     */
    public int moveField(int row, int delta) {
        int target = row + delta;
        if (row < 0 || row >= fields.size() || target < 0 || target >= fields.size()) {
            return row;
        }
        FieldModel moved = fields.remove(row);
        fields.add(target, moved);
        fireTableDataChanged();
        return target;
    }

    public int selectedCount() {
        int count = 0;
        for (FieldModel field : fields) {
            if (field.isSelected()) {
                count++;
            }
        }
        return count;
    }

    public void selectAll(boolean selected) {
        for (FieldModel field : fields) {
            field.setSelected(selected);
        }
        fireTableDataChanged();
    }

    private static String stringOf(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 界面里一行文本 -> 注释行列表。用换行符拆 (粘贴多行时也能正确处理)。
     */
    static List<String> splitComment(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        for (String line : text.split("\r\n|\r|\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}

# BeanForge

写 Spring 项目时，DTO / VO 基本上就是从 entity 里复制、删字段、加字段。这个插件把这件事变成一次 Alt+Enter。

## 用法

**在实体类里**按 `Alt+Enter` → `BeanForge: 生成 DTO / VO`

**在一个还不存在的类型上**按 `Alt+Enter`（例如 `Result<LoginVo> login` 里的 `LoginVo`）→
`BeanForge: 从实体生成 "LoginVo"`，它会按名字反推实体并预填类名。

两条路都会打开同一个对话框：选实体 → 勾字段 → 定选项 → OK。

## 对话框里能做什么

- **筛选字段**：默认全选，取消勾选即不生成；`全选 / 全不选`
- **改字段**：双击单元格直接改类型、字段名、注释
- **加字段**：`添加字段` 新增一行；手工字段会按类型自动补 import（`BigDecimal`、`LocalDateTime`、`List`…）
- **调顺序**：`上移 / 下移`
- **切实体**：顶部的实体下拉可搜索（`: 全限定名` 回车即可），打开时会后台扫描项目里的实体候选
- **切目标**：`dto / vo` 决定包名与类名的推断结果，两者都可手改

## 生成规则

| 项目 | 行为 |
|---|---|
| 持久层注解 | **去掉**：MyBatis、MyBatis-Plus、JPA、Hibernate（`@TableName`/`@TableId`/`@TableField`/`@TableLogic`/`@Column`…） |
| Lombok 注解 | **去掉原有的**再按选项重加（否则实体上的 `@Data` 和生成的 `@Data` 会重复，编译报错） |
| 其它注解 | **保留**，例如 swagger 的 `@Schema` |
| 字段注释 | Javadoc 原样搬（含 `<p>` 等），行尾 `// 注释` 转成 Javadoc；`@author`/`@date`/`@since`/`@version` 丢弃 |
| static 字段 | 不带（`serialVersionUID`、各类常量不进 DTO） |
| 默认注解 | `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor` |
| 可选 | `@Getter/@Setter`（与 `@Data` 互斥）、`@Builder`、`@Accessors(chain = true)`、`implements Serializable` |
| import | 只留真正用得上的（丢掉 `java.lang`、同包、类自己），排序输出 |

## 安全

- **目标类已存在时不会覆盖**：对话框里直接标红拦住。
- 生成过程包在一个 write command 里，**一次 Ctrl+Z 可整体撤销**。
- 只读检查不会创建目录 —— 取消对话框不会在磁盘上留下空包目录。

## 构建

需要一个 JDK 11+（IDEA 插件工程用 JDK 11 编译）。

```bash
./gradlew buildPlugin
```

产物在 `build/distributions/BeanForge-<version>.zip`，
在 IDEA 里 `Settings → Plugins → ⚙ → Install Plugin from Disk…` 装上即可。

## 验证

**不需要启动 IDE** —— 256 项断言 + 真实 `javac` 编译：

```bash
./gradlew compileJava   # 先编译插件源码
bash verify/run.sh
```

`verify/` 下是离线验证套件，分四组：

| 组 | 内容 |
|---|---|
| `BeanForgeCoreTest` | core 层 81 项：注解黑名单、Javadoc 搬运、类名推断、import 收集 |
| `ComboBoxValuesTest` | 下拉框状态机 26 项（真 `JComboBox`，headless） |
| `TypeCatalogTest` | 类型校验 + 字母检索 124 项（含表格模型层） |
| `TypeCellEditorTest` | 单元格编辑器 25 项（真 `JTable` + 真编辑器） |

其中"把生成结果交给**真实 javac** 编译"这一步会实际验证产物能否过编译
（需要本地 Maven 仓库里有 lombok 与 swagger-annotations，找不到则自动跳过这一步）。

设计上有一点是刻意为之的：`core/` 与 `ui/TypeFilterCombo`、`ui/TypeCellEditor`、
`ui/UiColors` **不依赖 IDEA 平台 API**，所以能脱离 IDE 单独编译和断言。
依赖 IDE API 的部分（`FieldPickerDialog` 等）由 `./gradlew compileJava` 覆盖。

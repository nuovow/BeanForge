import com.nuo.beanforge.core.AnnotationModel;
import com.nuo.beanforge.core.Annotations;
import com.nuo.beanforge.core.CommentUtils;
import com.nuo.beanforge.core.DtoVoGenerator;
import com.nuo.beanforge.core.EntityModel;
import com.nuo.beanforge.core.FieldModel;
import com.nuo.beanforge.core.GenerateOptions;
import com.nuo.beanforge.core.GenerationResult;
import com.nuo.beanforge.core.ImportCollector;
import com.nuo.beanforge.core.Naming;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BeanForge core 层离线断言。
 *
 * 不启动 IDE, 直接喂 EntityModel 断言生成出来的源码文本 —— 本轮改动里 90% 的逻辑都在这一层,
 * 所以这一层能用真实断言锁住, 就不用靠"在 IDE 里点一下看看"。
 */
public class BeanForgeCoreTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testPersistenceAnnotationFilter();
        testLombokAnnotationHandling();
        testCommentNormalization();
        testNaming();
        testImportCollector();
        testManualFieldImports();
        testSerializableAndBuilder();
        testFullSource();
        testEdgeCases();
        testGetterSetterFallback();
        testKeepCommentsOff();
        testSuperFieldsSwitch();

        System.out.println();
        System.out.println("--- 13. 把生成结果交给真实 javac ---");
        if (args.length > 0) {
            try {
                dumpGeneratedSources(args[0]);
                ok("导出生成结果到 " + args[0], true);
            } catch (Exception e) {
                record("导出生成结果", false, e.toString());
            }
        } else {
            System.out.println("  (没传输出目录, 跳过导出; 编译验证由 run.sh 负责)");
        }

        System.out.println("PASS=" + passed + " FAIL=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * 导出几个有代表性的生成结果, 让 run.sh 用真实 javac 编一遍。
     *
     * <p>文本断言只能证明"我以为的样子", 编译器才能证明 import 齐了、注解不冲突、语法没错。</p>
     */
    private static void dumpGeneratedSources(String rootDir) throws IOException {
        Path root = Paths.get(rootDir);
        Files.createDirectories(root);

        // 1) 默认选项 (Lombok 三件套 + 保留 swagger 注解)
        writeFile(root, "com/nuo/nuouser/dto/UserDTO.java",
            DtoVoGenerator.generate(sampleEntity(), optionsFor("com.nuo.nuouser.dto", "UserDTO")));

        // 2) 序列化 + Builder + 链式访问
        GenerateOptions voOptions = optionsFor("com.nuo.nuouser.vo", "UserVO");
        voOptions.setSerializable(true);
        voOptions.setLombokBuilder(true);
        voOptions.setLombokAccessorsChain(true);
        writeFile(root, "com/nuo/nuouser/vo/UserVO.java",
            DtoVoGenerator.generate(sampleEntity(), voOptions));

        // 3) 全手工字段: 类型靠查表补 import (BigDecimal / LocalDateTime / List / Map)
        EntityModel manual = new EntityModel();
        manual.setPackageName("com.nuo.nuouser.entity");
        manual.setClassName("Order");
        List<FieldModel> manualFields = new ArrayList<>();
        manualFields.add(manualField("amount", "BigDecimal", "金额"));
        manualFields.add(manualField("createdAt", "LocalDateTime", "创建时间"));
        manualFields.add(manualField("tags", "List<String>", "标签"));
        manualFields.add(manualField("snapshot", "Map<String, Object>", "快照"));
        manual.setFields(manualFields);
        writeFile(root, "com/nuo/nuouser/dto/OrderDTO.java",
            DtoVoGenerator.generate(manual, optionsFor("com.nuo.nuouser.dto", "OrderDTO")));

        // 4) 一个 Lombok 都不加: 纯字段类也要能编译
        GenerateOptions plain = optionsFor("com.nuo.nuouser.dto", "PlainDTO");
        plain.setLombokData(false);
        plain.setLombokGetterSetter(false);
        plain.setLombokNoArgsConstructor(false);
        plain.setLombokAllArgsConstructor(false);
        writeFile(root, "com/nuo/nuouser/dto/PlainDTO.java",
            DtoVoGenerator.generate(sampleEntity(), plain));

        // 5) @Getter/@Setter 退化路线
        GenerateOptions getterSetter = optionsFor("com.nuo.nuouser.dto", "GetterSetterDTO");
        getterSetter.setLombokData(false);
        getterSetter.setLombokGetterSetter(true);
        writeFile(root, "com/nuo/nuouser/dto/GetterSetterDTO.java",
            DtoVoGenerator.generate(sampleEntity(), getterSetter));
    }

    private static void writeFile(Path root, String relativePath, GenerationResult result) throws IOException {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, result.getSource().getBytes(StandardCharsets.UTF_8));
    }

    private static GenerateOptions optionsFor(String packageName, String className) {
        GenerateOptions options = new GenerateOptions();
        options.setTargetPackage(packageName);
        options.setTargetClassName(className);
        return options;
    }

    private static FieldModel manualField(String name, String type, String comment) {
        FieldModel field = new FieldModel();
        field.setName(name);
        field.setTypeText(type);
        field.setComments(new ArrayList<>(Arrays.asList(comment)));
        field.setManual(true);
        return field;
    }

    // ------------------------------------------------------------------ 1. 持久层注解

    private static void testPersistenceAnnotationFilter() {
        section("1. 去掉 MyBatis / JPA 注解, 保留别的注解");

        ok("MyBatis-Plus TableName 被识别",
            Annotations.isPersistenceAnnotation("com.baomidou.mybatisplus.annotation.TableName", "TableName"));
        ok("MyBatis-Plus TableId 被识别",
            Annotations.isPersistenceAnnotation("com.baomidou.mybatisplus.annotation.TableId", "TableId"));
        ok("MyBatis-Plus TableField 被识别",
            Annotations.isPersistenceAnnotation("com.baomidou.mybatisplus.annotation.TableField", "TableField"));
        ok("MyBatis-Plus TableLogic 被识别",
            Annotations.isPersistenceAnnotation("com.baomidou.mybatisplus.annotation.TableLogic", "TableLogic"));
        ok("JPA jakarta Column 被识别",
            Annotations.isPersistenceAnnotation("jakarta.persistence.Column", "Column"));
        ok("Hibernate 注解被识别",
            Annotations.isPersistenceAnnotation("org.hibernate.annotations.Where", "Where"));
        ok("解析不出全限定名时按简单名兜底 (TableField)",
            Annotations.isPersistenceAnnotation(null, "TableField"));
        ok("swagger 注解没被误伤",
            !Annotations.isPersistenceAnnotation("io.swagger.v3.oas.annotations.media.Schema", "Schema"));
        ok("validation 注解没被误伤",
            !Annotations.isPersistenceAnnotation("jakarta.validation.constraints.NotNull", "NotNull"));
        ok("自定义注解没被误伤",
            !Annotations.isPersistenceAnnotation("com.nuo.common.MyFlag", "MyFlag"));

        EntityModel entity = sampleEntity();
        GenerateOptions options = defaultOptions();
        List<AnnotationModel> kept = DtoVoGenerator.filterAnnotations(entity.getClassAnnotations(), options);
        check("类注解只留下 @Schema", simpleNames(kept), Arrays.asList("Schema"));

        List<AnnotationModel> fieldKept =
            DtoVoGenerator.filterAnnotations(entity.getFields().get(0).getAnnotations(), options);
        check("字段 @TableId 被剔除", simpleNames(fieldKept), Arrays.asList("Schema"));
    }

    // ------------------------------------------------------------------ 2. Lombok

    private static void testLombokAnnotationHandling() {
        section("2. Lombok 注解: 去掉原有的, 按选项重加");

        ok("lombok.Data 被识别为 Lombok 注解",
            Annotations.isLombokAnnotation("lombok.Data", "Data"));
        ok("lombok.experimental.Accessors 被识别",
            Annotations.isLombokAnnotation("lombok.experimental.Accessors", "Accessors"));
        ok("简单名兜底 (NoArgsConstructor)",
            Annotations.isLombokAnnotation(null, "NoArgsConstructor"));
        ok("swagger Schema 不是 Lombok",
            !Annotations.isLombokAnnotation("io.swagger.v3.oas.annotations.media.Schema", "Schema"));

        EntityModel entity = sampleEntity();
        List<AnnotationModel> kept = DtoVoGenerator.filterAnnotations(
            entity.getClassAnnotations(), defaultOptions());
        for (AnnotationModel annotation : kept) {
            ok("保留下来的注解里没有 Lombok: " + annotation,
                !Annotations.isLombokAnnotation(annotation.getQualifiedName(), annotation.getSimpleName()));
        }

        // 实体上本来有 @Data, 再生成一个 @Data 会编译报"重复注解", 所以必须只留生成的那一个
        GenerationResult result = DtoVoGenerator.generate(entity, defaultOptions());
        check("成品里 @Data 恰好出现一次", countOccurrences(result.getSource(), "@Data"), 1);
    }

    // ------------------------------------------------------------------ 3. 注释

    private static void testCommentNormalization() {
        section("3. 注释归一化");

        check("多行 Javadoc",
            CommentUtils.fromJavadoc("/**\n * 用户账号\n * <p>登录用</p>\n */"),
            Arrays.asList("用户账号", "<p>登录用</p>"));
        check("单行 Javadoc",
            CommentUtils.fromJavadoc("/** 主键 */"),
            Arrays.asList("主键"));
        check("@author 被丢掉",
            CommentUtils.fromJavadoc("/**\n * 用户\n * @author nuo\n */"),
            Arrays.asList("用户"));
        check("@author 的续行也一起丢掉",
            CommentUtils.fromJavadoc("/**\n * 用户\n * @author nuo\n * 2026-09-24\n */"),
            Arrays.asList("用户"));
        check("行尾 // 注释",
            CommentUtils.fromLineComment("// 创建时间"),
            Arrays.asList("创建时间"));
        check("块注释 /* */",
            CommentUtils.fromLineComment("/* 余额 */"),
            Arrays.asList("余额"));
        check("首尾空行被裁掉",
            CommentUtils.fromJavadoc("/**\n *\n * 内容\n *\n */"),
            Arrays.asList("内容"));
        check("注释里的 */ 被转义, 不会提前闭合",
            CommentUtils.renderJavadoc(Arrays.asList("a */ b"), "    "),
            "    /** a *&#47; b */");
        check("单行注释渲染成一行",
            CommentUtils.renderJavadoc(Arrays.asList("主键"), "    "),
            "    /** 主键 */");
        check("多行注释渲染成块",
            CommentUtils.renderJavadoc(Arrays.asList("第一行", "第二行"), "    "),
            "    /**\n     * 第一行\n     * 第二行\n     */");
    }

    // ------------------------------------------------------------------ 4. 命名

    private static void testNaming() {
        section("4. 包名 / 类名推断");

        check("entity 包 -> dto 包",
            Naming.guessTargetPackage("com.nuo.nuouser.entity", "dto"), "com.nuo.nuouser.dto");
        check("domain 包 -> vo 包",
            Naming.guessTargetPackage("com.nuo.shop.domain", "vo"), "com.nuo.shop.vo");
        check("po 包 -> dto 包",
            Naming.guessTargetPackage("com.nuo.shop.po", "dto"), "com.nuo.shop.dto");
        check("非实体包就往后追加",
            Naming.guessTargetPackage("com.nuo.shop", "dto"), "com.nuo.shop.dto");
        check("根包为空也不炸",
            Naming.guessTargetPackage("", "vo"), "vo");

        check("UserEntity -> UserDTO",
            Naming.guessClassName("UserEntity", "dto"), "UserDTO");
        check("User -> UserVO",
            Naming.guessClassName("User", "vo"), "UserVO");
        check("SysUserDO -> SysUserDTO",
            Naming.guessClassName("SysUserDO", "dto"), "SysUserDTO");
        check("幂等: UserDTO 转 dto 还是 UserDTO",
            Naming.guessClassName("UserDTO", "dto"), "UserDTO");
        check("UserDTO 转 vo -> UserVO",
            Naming.guessClassName("UserDTO", "vo"), "UserVO");

        check("反推: LoginVo 剥成 Login", Naming.stripKnownSuffix("LoginVo"), "Login");
        check("反推: UserEntity 剥成 User", Naming.stripKnownSuffix("UserEntity"), "User");
        check("反推: User 保持 User", Naming.stripKnownSuffix("User"), "User");
    }

    // ------------------------------------------------------------------ 5. import

    private static void testImportCollector() {
        section("5. import 收集");

        ImportCollector.Result result = ImportCollector.collect(Arrays.asList(
            "java.lang.String",              // java.lang 不要
            "java.lang.annotation.Retention", // 例外: 要
            "java.util.List",
            "java.util.List",                // 重复
            "com.nuo.dto.UserDTO",           // 类自己不要
            "com.nuo.dto.Helper",            // 同包不要
            "int",                           // 没包名不要
            "java.math.BigDecimal"
        ), "com.nuo.dto", "UserDTO");

        check("收集到的 import", result.getStatements(), Arrays.asList(
            "import java.lang.annotation.Retention;",
            "import java.math.BigDecimal;",
            "import java.util.List;"
        ));
        ok("没有冲突告警", result.getWarnings().isEmpty());

        ImportCollector.Result conflict = ImportCollector.collect(Arrays.asList(
            "java.util.Date",
            "java.sql.Date"
        ), "com.nuo.dto", "UserDTO");
        check("同名冲突时只留一个 import", conflict.getStatements(), Arrays.asList("import java.util.Date;"));
        ok("同名冲突有告警", conflict.getWarnings().size() == 1);
    }

    // ------------------------------------------------------------------ 6. 手动字段

    private static void testManualFieldImports() {
        section("6. 手工添加的字段按类型补 import");

        EntityModel entity = new EntityModel();
        entity.setPackageName("com.nuo.entity");
        entity.setClassName("User");

        FieldModel manual = new FieldModel();
        manual.setName("amount");
        manual.setTypeText("BigDecimal");
        manual.setManual(true);
        manual.setComments(Arrays.asList("金额"));
        entity.setFields(new ArrayList<>(Arrays.asList(manual)));

        EntityModel withTags = entity;
        FieldModel tags = new FieldModel();
        tags.setName("tagList");
        tags.setTypeText("List<String>");
        tags.setManual(true);
        withTags.getFields().add(tags);

        GenerateOptions options = defaultOptions();
        GenerationResult result = DtoVoGenerator.generate(withTags, options);
        contains("BigDecimal 自动补 import", result.getSource(), "import java.math.BigDecimal;");
        contains("List 自动补 import", result.getSource(), "import java.util.List;");
        ok("String 不该被补 import", !result.getSource().contains("import java.lang.String;"));
        contains("手动字段的注释也在", result.getSource(), "/** 金额 */");
        contains("手动字段本体", result.getSource(), "private BigDecimal amount;");
    }

    // ------------------------------------------------------------------ 7. Serializable / Builder

    private static void testSerializableAndBuilder() {
        section("7. Serializable 与 Builder / Accessors");

        GenerateOptions options = defaultOptions();
        options.setTargetClassName("UserDTO");
        options.setSerializable(true);
        options.setLombokBuilder(true);
        options.setLombokAccessorsChain(true);

        GenerationResult result = DtoVoGenerator.generate(sampleEntity(), options);
        contains("implements Serializable", result.getSource(), "public class UserDTO implements Serializable {");
        contains("serialVersionUID", result.getSource(), "private static final long serialVersionUID = 1L;");
        contains("Serializable import", result.getSource(), "import java.io.Serializable;");
        contains("@Builder", result.getSource(), "@Builder");
        contains("@Accessors(chain = true)", result.getSource(), "@Accessors(chain = true)");
        contains("Accessors import", result.getSource(), "import lombok.experimental.Accessors;");
    }

    // ------------------------------------------------------------------ 8. 完整输出

    private static void testFullSource() {
        section("8. 完整源码逐行比对");

        GenerateOptions options = defaultOptions();
        options.setTargetPackage("com.nuo.nuouser.dto");
        options.setTargetClassName("UserDTO");

        GenerationResult result = DtoVoGenerator.generate(sampleEntity(), options);
        String expected = String.join("\n", Arrays.asList(
            "package com.nuo.nuouser.dto;",
            "",
            "import io.swagger.v3.oas.annotations.media.Schema;",
            "import java.math.BigDecimal;",
            "import java.time.LocalDateTime;",
            "import java.util.List;",
            "import lombok.AllArgsConstructor;",
            "import lombok.Data;",
            "import lombok.NoArgsConstructor;",
            "",
            "/** 用户信息 */",
            "@Schema(name = \"User\")",
            "@Data",
            "@NoArgsConstructor",
            "@AllArgsConstructor",
            "public class UserDTO {",
            "",
            "    /** 主键 */",
            "    @Schema(description = \"主键\")",
            "    private Long id;",
            "",
            "    /**",
            "     * 用户账号",
            "     * <p>登录用</p>",
            "     */",
            "    private String userAccount;",
            "",
            "    /** 余额 */",
            "    private BigDecimal balance;",
            "",
            "    /** 标签 */",
            "    private List<String> tags;",
            "",
            "    /** 创建时间 */",
            "    private LocalDateTime createTime;",
            "",
            "    /** 逻辑删除 */",
            "    private Integer deleted;",
            "}",
            ""
        ));
        check("生成的源码与预期完全一致", result.getSource(), expected);
        ok("没有告警", result.getWarnings().isEmpty());
        check("参与生成的字段数", result.getUsedFields().size(), 6);
    }

    // ------------------------------------------------------------------ 9. 边界

    private static void testEdgeCases() {
        section("9. 边界情况");

        EntityModel entity = sampleEntity();
        GenerateOptions options = defaultOptions();

        // 取消勾选
        entity.getFields().get(2).setSelected(false);
        GenerationResult unselected = DtoVoGenerator.generate(entity, options);
        ok("取消勾选的字段不出现",
            !unselected.getSource().contains("balance"));
        check("字段数少了 1", unselected.getUsedFields().size(), 5);
        entity.getFields().get(2).setSelected(true);

        // 非法字段名
        entity.getFields().get(3).setName("class");
        GenerationResult invalidName = DtoVoGenerator.generate(entity, options);
        ok("关键字字段名被跳过", !invalidName.getSource().contains("private List<String> class;"));
        check("跳过时有告警", invalidName.getWarnings().size(), 1);

        // 空类型
        entity.getFields().get(3).setName("tags");
        entity.getFields().get(3).setTypeText("");
        GenerationResult emptyType = DtoVoGenerator.generate(entity, options);
        ok("空类型被跳过", !emptyType.getSource().contains("private  tags;"));
        entity.getFields().get(3).setTypeText("List<String>");

        // 全部取消
        for (FieldModel field : entity.getFields()) {
            field.setSelected(false);
        }
        GenerateResult empty = new GenerateResult(DtoVoGenerator.generate(entity, options));
        // options 里没指定类名, 所以应该回落到实体名
        ok("一个字段都不选时仍能生成空类", empty.source.contains("public class UserEntity {"));
        ok("空类里没有字段声明", !empty.source.contains("private "));

        // 父类字段
        EntityModel withSuper = sampleEntity();
        withSuper.getFields().get(5).setFromSuper(true);
        GenerationResult includeSuper = DtoVoGenerator.generate(withSuper, defaultOptions());
        ok("默认不带上父类字段", !includeSuper.getSource().contains("deleted"));
        GenerateOptions includeSuperOptions = defaultOptions();
        includeSuperOptions.setIncludeSuperFields(true);
        GenerationResult withSuperOut = DtoVoGenerator.generate(withSuper, includeSuperOptions);
        ok("勾选后带上父类字段", withSuperOut.getSource().contains("private Integer deleted;"));

        // 目标类名为空时回落到实体名
        GenerateOptions noClass = defaultOptions();
        noClass.setTargetClassName("");
        noClass.setTargetPackage("");
        GenerationResult fallback = DtoVoGenerator.generate(sampleEntity(), noClass);
        contains("类名为空时回落到实体名", fallback.getSource(),
            "public class UserEntity {");
    }

    // ------------------------------------------------------------------ 10. @Getter/@Setter

    private static void testGetterSetterFallback() {
        section("10. 不勾 @Data 时退化成 @Getter/@Setter");

        GenerateOptions options = defaultOptions();
        options.setLombokData(false);
        options.setLombokGetterSetter(true);
        GenerationResult result = DtoVoGenerator.generate(sampleEntity(), options);
        ok("没有 @Data", !result.getSource().contains("@Data\n"));
        contains("@Getter", result.getSource(), "@Getter");
        contains("@Setter", result.getSource(), "@Setter");
        contains("Getter import", result.getSource(), "import lombok.Getter;");
        contains("Setter import", result.getSource(), "import lombok.Setter;");

        // 两个都不勾: 纯字段类
        GenerateOptions plain = defaultOptions();
        plain.setLombokData(false);
        plain.setLombokGetterSetter(false);
        plain.setLombokNoArgsConstructor(false);
        plain.setLombokAllArgsConstructor(false);
        GenerationResult plainResult = DtoVoGenerator.generate(sampleEntity(), plain);
        ok("不做任何 Lombok 处理", !plainResult.getSource().contains("lombok"));
    }

    // ------------------------------------------------------------------ 11. 关掉注释

    private static void testKeepCommentsOff() {
        section("11. 关掉「保留注释」选项");

        GenerateOptions options = defaultOptions();
        options.setKeepComments(false);
        GenerationResult result = DtoVoGenerator.generate(sampleEntity(), options);
        ok("字段注释没带过来", !result.getSource().contains("用户账号"));
        ok("类注释也没了", !result.getSource().contains("用户信息"));
        ok("字段本身还在", result.getSource().contains("private String userAccount;"));
    }

    // ------------------------------------------------------------------ 12. @Data 与 @Getter/Setter 互斥

    private static void testSuperFieldsSwitch() {
        section("12. Lombok 注解组合只在选项之间产生");

        GenerateOptions options = defaultOptions();
        options.setLombokNoArgsConstructor(false);
        options.setLombokAllArgsConstructor(false);
        GenerationResult result = DtoVoGenerator.generate(sampleEntity(), options);
        ok("不勾构造器就不生成", !result.getSource().contains("Constructor"));
        ok("构造器 import 也不生成", !result.getSource().contains("lombok.NoArgsConstructor"));
    }

    // ------------------------------------------------------------------ 测试数据

    /**
     * 一个典型 MyBatis-Plus 实体: 注解齐全、注释多行、有行尾注释、有复杂类型。
     */
    private static EntityModel sampleEntity() {
        EntityModel entity = new EntityModel();
        entity.setPackageName("com.nuo.nuouser.entity");
        entity.setClassName("UserEntity");
        entity.setClassComments(Arrays.asList("用户信息"));
        entity.setClassAnnotations(Arrays.asList(
            annotation("TableName", "com.baomidou.mybatisplus.annotation.TableName", "@TableName(\"user\")"),
            annotation("Data", "lombok.Data", "@Data"),
            annotation("Schema", "io.swagger.v3.oas.annotations.media.Schema", "@Schema(name = \"User\")")
        ));

        List<FieldModel> fields = new ArrayList<>();
        fields.add(field("id", "Long", Arrays.asList("主键"),
            Arrays.asList("java.lang.Long"),
            annotation("TableId", "com.baomidou.mybatisplus.annotation.TableId",
                "@TableId(type = IdType.AUTO)"),
            annotation("Schema", "io.swagger.v3.oas.annotations.media.Schema",
                "@Schema(description = \"主键\")")));
        fields.add(field("userAccount", "String", Arrays.asList("用户账号", "<p>登录用</p>"),
            Arrays.asList("java.lang.String"), annotation("TableField",
                "com.baomidou.mybatisplus.annotation.TableField", "@TableField(\"user_account\")")));
        FieldModel balance = field("balance", "BigDecimal", Arrays.asList("余额"),
            Arrays.asList("java.math.BigDecimal"));
        fields.add(balance);
        fields.add(field("tags", "List<String>", Arrays.asList("标签"),
            Arrays.asList("java.util.List", "java.lang.String")));
        fields.add(field("createTime", "LocalDateTime", Arrays.asList("创建时间"),
            Arrays.asList("java.time.LocalDateTime")));
        fields.add(field("deleted", "Integer", Arrays.asList("逻辑删除"),
            Arrays.asList("java.lang.Integer"), annotation("TableLogic",
                "com.baomidou.mybatisplus.annotation.TableLogic", "@TableLogic")));
        entity.setFields(fields);
        return entity;
    }

    private static FieldModel field(String name, String type, List<String> comments, List<String> imports,
                                    AnnotationModel... annotations) {
        FieldModel field = new FieldModel();
        field.setName(name);
        field.setTypeText(type);
        field.setComments(new ArrayList<>(comments));
        field.setImportHints(new ArrayList<>(imports));
        field.setAnnotations(new ArrayList<>(Arrays.asList(annotations)));
        return field;
    }

    private static AnnotationModel annotation(String simpleName, String qualifiedName, String text) {
        return new AnnotationModel(simpleName, qualifiedName, text);
    }

    private static GenerateOptions defaultOptions() {
        return new GenerateOptions();
    }

    private static List<String> simpleNames(List<AnnotationModel> annotations) {
        List<String> names = new ArrayList<>();
        for (AnnotationModel annotation : annotations) {
            names.add(annotation.getSimpleName());
        }
        return names;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = text.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
    }

    // ------------------------------------------------------------------ 断言工具

    private static void section(String title) {
        System.out.println();
        System.out.println("--- " + title + " ---");
    }

    private static void ok(String label, boolean condition) {
        record(label, condition, condition ? null : "期望为真, 实际为假");
    }

    private static void check(String label, Object actual, Object expected) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        record(label, same, same ? null : "期望 <" + expected + "> 实际 <" + actual + ">");
    }

    private static void contains(String label, String text, String needle) {
        boolean found = text != null && text.contains(needle);
        record(label, found, found ? null : "文本里找不到 <" + needle + ">");
    }

    private static void record(String label, boolean success, String detail) {
        if (success) {
            passed++;
            System.out.println("  [PASS] " + label);
        } else {
            failed++;
            System.out.println("  [FAIL] " + label + (detail == null ? "" : " -> " + detail));
        }
    }

    /**
     * 只是为了把 GenerationResult 包一层好写 (避免检查里出现超长链式调用)。
     */
    private static class GenerateResult {
        final String source;

        GenerateResult(GenerationResult result) {
            this.source = result.getSource();
        }
    }
}

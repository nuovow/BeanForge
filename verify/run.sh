#!/usr/bin/env bash
# BeanForge 离线验证 —— 不需要启动 IDE。
#
#   bash verify/run.sh
#
# 做的事:
#   1. core 层断言 (注解过滤 / 注释搬运 / 命名推断 / import 收集)
#   2. 把生成出来的类交给**真实 javac** 编译, 验证产物真能过编译
#   3. Swing 组件断言 (下拉框状态、类型校验与字母检索、单元格编辑器双击进编辑)
#   4. 编译"不依赖 IDEA 平台"的那些类, 确认它们仍是纯 Swing 实现
#
# 可覆盖的环境变量:
#   JAVA_HOME   指定 JDK (默认用 PATH 上的 java; 需要 JDK 11+)
#   MAVEN_REPO  Maven 本地仓库 (用来找 lombok / swagger jar; 找不到就跳过第 2 步)
set -e

# pwd -W: javac 是原生程序, 不认 git-bash 的 /c/... 路径形式
HERE="$(cd "$(dirname "$0")" && pwd -W)"
PROJECT="$(cd "$HERE/.." && pwd -W)"
CORE="$PROJECT/build/classes/java/main"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
  JAVA="$JAVA_HOME/bin/java"
else
  JAVAC="$(command -v javac)"
  JAVA="$(command -v java)"
fi

if [ -z "$JAVAC" ]; then
  echo "找不到 javac。请装 JDK 11+ 或设置 JAVA_HOME。" >&2
  exit 1
fi

# Maven 本地仓库: 环境变量 > 常见默认位置
if [ -z "$MAVEN_REPO" ]; then
  for candidate in "$HOME/.m2/repository" "/d/apache-maven-3.9.16/repository"; do
    if [ -d "$candidate" ]; then
      MAVEN_REPO="$candidate"
      break
    fi
  done
fi

if [ ! -d "$CORE" ]; then
  echo "还没编译插件源码。先执行: cd \"$PROJECT\" && ./gradlew compileJava" >&2
  exit 1
fi

rm -rf "$HERE/out" "$HERE/out-type" "$HERE/out-editor" "$HERE/gen-out" "$HERE/generated"
mkdir -p "$HERE/out" "$HERE/out-type" "$HERE/out-editor" "$HERE/gen-out"

echo "=== 1. core 层断言 ==="
"$JAVAC" -encoding UTF-8 -cp "$CORE" -d "$HERE/out" "$HERE/BeanForgeCoreTest.java"
"$JAVA" -Dfile.encoding=UTF-8 -cp "$HERE/out;$CORE" BeanForgeCoreTest "$HERE/generated"

echo
echo "=== 2. 用真实 javac 编译生成出来的类 ==="
LOMBOK=""
SWAGGER=""
if [ -n "$MAVEN_REPO" ] && [ -d "$MAVEN_REPO" ]; then
  LOMBOK="$(ls "$MAVEN_REPO"/org/projectlombok/lombok/*/lombok-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
  SWAGGER="$(find "$MAVEN_REPO"/io/swagger/core/v3 -name 'swagger-annotations-jakarta-*.jar' 2>/dev/null | grep -v sources | head -1 || true)"
fi

if [ -n "$LOMBOK" ] && [ -n "$SWAGGER" ]; then
  echo "lombok  = $(basename "$LOMBOK")"
  echo "swagger = $(basename "$SWAGGER")"
  # -proc:none: 只验语法与 import, 不让 Lombok 注解处理器参与进来
  "$JAVAC" -encoding UTF-8 -proc:none -cp "$LOMBOK;$SWAGGER" -d "$HERE/gen-out" \
    $(find "$HERE/generated" -name "*.java")
  echo "javac 通过: $(find "$HERE/gen-out" -name '*.class' | wc -l) 个 class, " \
       "源自 $(find "$HERE/generated" -name '*.java' | wc -l) 个 .java"
else
  echo "跳过 (没找到 lombok / swagger jar; 可设 MAVEN_REPO 指向本地 Maven 仓库)"
fi

echo
echo "=== 3. 下拉框状态断言 (真 Swing 组件, headless) ==="
"$JAVAC" -encoding UTF-8 -cp "$CORE" -d "$HERE/out" "$HERE/ComboBoxValuesTest.java"
"$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp "$HERE/out;$CORE" ComboBoxValuesTest

echo
echo "=== 4. 类型列校验 + 字母检索断言 ==="
"$JAVAC" -encoding UTF-8 -cp "$CORE" -d "$HERE/out-type" "$HERE/TypeCatalogTest.java"
"$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp "$HERE/out-type;$CORE" TypeCatalogTest

echo
echo "=== 5. 单元格编辑器: 双击进入编辑 / 编辑器复用残留 ==="
"$JAVAC" -encoding UTF-8 -cp "$CORE" -d "$HERE/out-editor" "$HERE/TypeCellEditorTest.java"
"$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp "$HERE/out-editor;$CORE" TypeCellEditorTest

echo
echo "=== 6. 用真实 javac 编译\"不依赖 IDE 平台\"的 UI 类 ==="
# TypeFilterCombo / TypeCellEditor / UiColors 只依赖 Swing + core,
# 所以能脱离 IDEA 平台单独编译并跑断言; FieldPickerDialog 依赖 IDE API, 由 ./gradlew compileJava 覆盖
"$JAVAC" -encoding UTF-8 -cp "$CORE" -d "$HERE/gen-out" \
  "$PROJECT/src/main/java/com/nuo/beanforge/ui/TypeFilterCombo.java" \
  "$PROJECT/src/main/java/com/nuo/beanforge/ui/TypeCellEditor.java" \
  "$PROJECT/src/main/java/com/nuo/beanforge/ui/UiColors.java" \
  "$PROJECT/src/main/java/com/nuo/beanforge/ui/ComboBoxValues.java" \
  "$PROJECT/src/main/java/com/nuo/beanforge/core/TypeCatalog.java"
echo "javac 通过: $(find "$HERE/gen-out/com/nuo/beanforge" -name '*.class' | wc -l) 个 class"

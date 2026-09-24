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
#   JAVA_HOME   指定 JDK (默认用 PATH 上的 javac; 需要 JDK 11+)
#   MAVEN_REPO  Maven 本地仓库 (用来找 lombok / swagger jar; 默认自动探测, 找不到就跳过第 2 步)
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

# 把路径转成"原生程序(javac)认得的"形式。
# git-bash 下 $HOME 是 /c/Users/xxx 这种 MSYS 路径, 原生 javac 不认, 且**不报错**——
# 表现为 jar 被静默忽略, 然后一堆 "程序包 lombok 不存在"。pwd -W 给出 Windows 形式;
# 非 MSYS 环境没有 -W 选项, 回退原值。
win_path() {
  local p="$1" w
  [ -n "$p" ] || return 0
  w="$(cd "$p" 2>/dev/null && pwd -W 2>/dev/null)" || true
  if [ -n "$w" ]; then printf '%s' "$w"; else printf '%s' "$p"; fi
}

# Maven 本地仓库: 环境变量优先; 否则自动探测。
# 注意不能"取第一个存在的目录"就行 —— 实测 ~/.m2/repository 里常有 lombok 却没有
# swagger-annotations-jakarta, 那样第 2 步会被静默跳过。所以优先选"两者都齐"的仓库,
# 都不齐时退回到第一个存在的目录 (至少能少跳一点)。
if [ -z "$MAVEN_REPO" ]; then
  MAVEN_REPO=""
  FALLBACK=""
  for candidate in "$HOME/.m2/repository" \
                   "/d/apache-maven-3.9.16/repository" \
                   "/c/apache-maven-3.9.16/repository"; do
    [ -d "$candidate" ] || continue
    [ -n "$FALLBACK" ] || FALLBACK="$candidate"
    if [ -n "$(find "$candidate/org/projectlombok/lombok" -name 'lombok-*.jar' \
                 ! -name '*sources*' 2>/dev/null | head -1)" ] &&
       [ -n "$(find "$candidate/io/swagger/core/v3" -name 'swagger-annotations-jakarta-*.jar' \
                 ! -name '*sources*' 2>/dev/null | head -1)" ]; then
      MAVEN_REPO="$candidate"
      break
    fi
  done
  [ -n "$MAVEN_REPO" ] || MAVEN_REPO="$FALLBACK"
fi

# 无论来自环境变量还是自动探测, 都要换成原生形式再交给 javac
MAVEN_REPO="$(win_path "$MAVEN_REPO")"

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
  # sort -V 取最高版本, 保证不同机器上选到的是同一个 jar
  LOMBOK="$(find "$MAVEN_REPO/org/projectlombok/lombok" -name 'lombok-*.jar' \
            ! -name '*sources*' 2>/dev/null | sort -V | tail -1)"
  SWAGGER="$(find "$MAVEN_REPO/io/swagger/core/v3" -name 'swagger-annotations-jakarta-*.jar' \
            ! -name '*sources*' 2>/dev/null | sort -V | tail -1)"
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
  echo "跳过 (仓库里缺 lombok 或 swagger-annotations-jakarta; 可用 MAVEN_REPO=... 指定)"
  echo "  当前探测到的仓库: ${MAVEN_REPO:-<无>}"
  echo "  生成的源码仍保留在 $HERE/generated, 可手工编译"
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

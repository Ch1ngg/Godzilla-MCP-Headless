#!/bin/sh
# ============================================================
# Godzilla-MCP Headless · 无 Maven 构建脚本
# 依赖: JDK 8+ ；lib/godzilla.jar（或通过 GZ_HOME 指定）
# 产出: dist/godzilla-mcp-<version>.jar（内嵌 Gson，单文件直接可用）
# ============================================================
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$REPO_DIR"

VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n1)
echo "版本: $VERSION"

# ---- 定位 godzilla.jar ----
GZ_JAR=""
for f in "${GZ_HOME:-}/godzilla.jar" "lib/godzilla.jar" "$HOME/Godzilla/godzilla.jar"; do
    if [ -n "$f" ] && [ -f "$f" ]; then GZ_JAR="$f"; break; fi
done
if [ -z "$GZ_JAR" ]; then
    echo "[!] 未找到 godzilla.jar：请复制到 lib/ 或设置 GZ_HOME=/path/to/Godzilla" >&2
    exit 1
fi
GZ_JAR=$(CDPATH= cd -- "$(dirname -- "$GZ_JAR")" && pwd)/$(basename -- "$GZ_JAR")
echo "godzilla.jar: $GZ_JAR"

# ---- 准备 Gson ----
GSON_JAR="lib/gson-2.10.1.jar"
if [ ! -f "$GSON_JAR" ]; then
    echo "下载 Gson 2.10.1 ..."
    mkdir -p lib
    curl -fsSL -o "$GSON_JAR" \
        https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar \
        || { echo "[!] Gson 下载失败，请手动下载放置到 $GSON_JAR" >&2; exit 1; }
fi
GSON_JAR=$(CDPATH= cd -- "$(dirname -- "$GSON_JAR")" && pwd)/$(basename -- "$GSON_JAR")

# ---- 编译 ----
JAVAC="${JAVAC:-javac}"
rm -rf target/classes target/pkg
mkdir -p target/classes target/pkg dist
find src -name "*.java" > target/sources.txt
if "$JAVAC" -version 2>&1 | grep -q '1\.8'; then
    "$JAVAC" -encoding UTF-8 -source 8 -target 8 -cp "$GZ_JAR:$GSON_JAR" -d target/classes @target/sources.txt
else
    "$JAVAC" -encoding UTF-8 --release 8 -cp "$GZ_JAR:$GSON_JAR" -d target/classes @target/sources.txt
fi

# ---- 打包（类 + 内嵌 Gson）----
cp -R target/classes/. target/pkg/
( cd target/pkg && unzip -o -q "$GSON_JAR" )
rm -rf target/pkg/META-INF/versions target/pkg/META-INF/MANIFEST.MF
JAR_OUT="dist/godzilla-mcp-$VERSION.jar"
( cd target/pkg && jar cf "$REPO_DIR/$JAR_OUT" . )
echo "✓ 构建完成: $JAR_OUT"
echo "  用法见 README；stdio: scripts/run-stdio.sh  |  HTTP: scripts/run-http.sh"

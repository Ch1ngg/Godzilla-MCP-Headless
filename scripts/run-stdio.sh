#!/bin/sh
# ============================================================
# Godzilla-MCP Headless · stdio 模式启动器
# 供 MCP 客户端（Claude Desktop / Claude Code / Cursor 等）作为 command 调用；
# 也可直接在终端运行。
#
# 可选环境变量：
#   GZ_HOME      godzilla.jar 所在目录（从中获取运行时核心类）
#                默认依次探测: 仓库内 ./godzilla → ~/Godzilla → 当前目录
#   MCP_WORKDIR  data.db 工作目录（决定使用哪个数据库；默认同 GZ_HOME）
#   MCP_JAR      插件 JAR 路径（默认自动查找 dist/ 或 target/ 下的 godzilla-mcp-*.jar）
#   JAVA         java 可执行文件（默认 java）
# ============================================================
set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

# ---- 定位插件 JAR ----
if [ -z "${MCP_JAR:-}" ]; then
    for f in "$REPO_DIR"/dist/godzilla-mcp-*.jar "$REPO_DIR"/godzilla-mcp-*.jar "$REPO_DIR"/target/godzilla-mcp-*.jar "$SCRIPT_DIR"/godzilla-mcp-*.jar; do
        if [ -f "$f" ]; then MCP_JAR="$f"; break; fi
    done
fi
if [ -z "${MCP_JAR:-}" ] || [ ! -f "$MCP_JAR" ]; then
    echo "[!] 未找到插件 JAR。请先构建（见 README「构建」），或设置 MCP_JAR=/path/to/godzilla-mcp-*.jar" >&2
    exit 1
fi

# ---- 定位哥斯拉目录（godzilla.jar）----
if [ -z "${GZ_HOME:-}" ]; then
    for d in "$REPO_DIR/godzilla" "$HOME/Godzilla" "$PWD"; do
        if [ -f "$d/godzilla.jar" ]; then GZ_HOME="$d"; break; fi
    done
fi
if [ -z "${GZ_HOME:-}" ] || [ ! -f "$GZ_HOME/godzilla.jar" ]; then
    echo "[!] 未找到 godzilla.jar。请设置 GZ_HOME 指向你的哥斯拉安装目录，例如：" >&2
    echo "    export GZ_HOME=/path/to/Godzilla" >&2
    exit 1
fi

# ---- data.db 工作目录 ----
WORKDIR="${MCP_WORKDIR:-$GZ_HOME}"
cd "$WORKDIR" || exit 1

JAVA="${JAVA:-java}"
CP="$MCP_JAR:$GZ_HOME/godzilla.jar"

echo "[Godzilla-MCP] stdio 模式 | jar=$MCP_JAR | db=$(pwd)/data.db" >&2

# 无显示环境的 Linux 服务器：用 xvfb-run 提供虚拟显示
# （哥斯拉核心初始化会读取屏幕尺寸，纯 headless 下会抛异常）
if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    exec xvfb-run -a "$JAVA" -cp "$CP" shells.plugins.online.GodzillaMcpHeadlessBootstrap --stdio
else
    exec "$JAVA" -cp "$CP" shells.plugins.online.GodzillaMcpHeadlessBootstrap --stdio
fi

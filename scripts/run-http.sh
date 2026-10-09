#!/bin/sh
# ============================================================
# Godzilla-MCP Headless · HTTP/SSE 模式（常驻服务）
# 用法: ./run-http.sh [端口] [绑定地址]
#   端口默认为 5566，绑定地址默认为 127.0.0.1（勿直接暴露公网，本模式无鉴权）
#
# 可选环境变量：GZ_HOME / MCP_WORKDIR / MCP_JAR / JAVA 同 run-stdio.sh
#              MCP_PORT / MCP_HOST 对应位置参数
# ============================================================
set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

PORT="${1:-${MCP_PORT:-5566}}"
HOST="${2:-${MCP_HOST:-127.0.0.1}}"

if [ -z "${MCP_JAR:-}" ]; then
    for f in "$REPO_DIR"/dist/godzilla-mcp-*.jar "$REPO_DIR"/godzilla-mcp-*.jar "$REPO_DIR"/target/godzilla-mcp-*.jar "$SCRIPT_DIR"/godzilla-mcp-*.jar; do
        if [ -f "$f" ]; then MCP_JAR="$f"; break; fi
    done
fi
if [ -z "${MCP_JAR:-}" ] || [ ! -f "$MCP_JAR" ]; then
    echo "[!] 未找到插件 JAR。请先构建（见 README「构建」），或设置 MCP_JAR" >&2
    exit 1
fi

if [ -z "${GZ_HOME:-}" ]; then
    for d in "$REPO_DIR/godzilla" "$HOME/Godzilla" "$PWD"; do
        if [ -f "$d/godzilla.jar" ]; then GZ_HOME="$d"; break; fi
    done
fi
if [ -z "${GZ_HOME:-}" ] || [ ! -f "$GZ_HOME/godzilla.jar" ]; then
    echo "[!] 未找到 godzilla.jar。请设置 GZ_HOME=/path/to/Godzilla" >&2
    exit 1
fi

WORKDIR="${MCP_WORKDIR:-$GZ_HOME}"
cd "$WORKDIR" || exit 1

JAVA="${JAVA:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"
# macOS：隐藏 AWT 初始化触发的 Dock 图标（不影响读屏幕尺寸等调用）
if [ "$(uname -s)" = "Darwin" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dapple.awt.UIElement=true"
fi
CP="$MCP_JAR:$GZ_HOME/godzilla.jar"

echo "[Godzilla-MCP] HTTP 模式: http://$HOST:$PORT/mcp （Ctrl+C 退出）" >&2

if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    exec xvfb-run -a "$JAVA" $JAVA_OPTS -cp "$CP" shells.plugins.online.GodzillaMcpHeadlessBootstrap "$PORT" "$HOST"
else
    exec "$JAVA" $JAVA_OPTS -cp "$CP" shells.plugins.online.GodzillaMcpHeadlessBootstrap "$PORT" "$HOST"
fi

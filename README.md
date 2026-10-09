# Godzilla-MCP Headless · 哥斯拉 MCP 无头版

[![build](https://github.com/Ch1ngg/Godzilla-MCP-Headless/actions/workflows/build.yml/badge.svg)](https://github.com/Ch1ngg/Godzilla-MCP-Headless/actions/workflows/build.yml)

把 [hkdonline/Godzilla-MCP](https://github.com/hkdonline/Godzilla-MCP)（上游改版自 [cns1rius/godzilla-mcp](https://github.com/cns1rius/godzilla-mcp)）改造成**无需启动哥斯拉 GUI 即可运行**的 MCP 服务器。

- 🔌 **两种传输**：`stdio`（Claude Desktop / Claude Code / Cursor 直接拉起，零端口）与 `HTTP/SSE`（常驻服务，任意 MCP 客户端可接入）
- 🗂 **直连你的哥斯拉数据**：与哥斯拉共用同一个 `data.db`，已保存的 Webshell 直接可用
- 🧩 **同一份 JAR 双形态**：也可以放进哥斯拉 `shells/plugins/java/` 当插件用（菜单照常）
- 🛠 **44 个工具**：会话 / 文件 / 大文件传输 / 命令与代码执行 / 内存马 / 端口扫描 / 数据库 / 插件库管理 / 马生成
- ✅ 全链路实测：Docker Tomcat(JDK8) JSP 马 与 PHP 靶机（生成 → 上线 → 命令 → 文件 → 内存马 → 插件管理）

> 仅把自有 `godzilla.jar` 当类库使用，**不需要、也不会启动哥斯拉主程序**。

---

## 功能一览（56 个工具）

| 类别 | 工具 |
|---|---|
| 会话管理 | `list_shells` `add_shell` `connect_shell`（免保存直连）`disconnect_shell` `list_sessions` `test_connection` `remove_shell` |
| 生成/配置 | `generate_shell`（JSP/PHP/C#/ASP 全系马，14 种 cryption）`get_env_config` |
| 系统信息 | `get_basics_info` `current_user` `process_list` `network_info` `screenshot` |
| 命令/代码 | `exec_command` `exec_code`（PHP/ASP 代码执行）`real_cmd`（虚拟终端，PHP/Java/C#）|
| 文件操作 | `list_files` `read_file` `write_file` `upload_file` `download_file` `delete_file` `copy_file` `move_file` `new_file` `new_dir` `list_root` `file_size` `file_remote_down` `big_file_upload` |
| 数据库 | `exec_sql` `list_databases` `enum_database_conn` |
| 高级能力 | `port_scan`（Java/PHP/C#）`memory_shell_inject` `memory_shell_list` `memory_shell_unload` `filter_shell_add` `filter_shell_list` `filter_shell_remove` `zip`（压缩/解压，Java/PHP/C#）|
| PHP 专属 | `php_ps`（免命令进程列表）`php_webshell_scan`（Webshell 扫描）`php_bypass_open_basedir` `php_bypass_disable_functions`（mem/env/fpm/amc 四方式）`php_attack_fpm`（FastCGI 直打 FPM）|
| Windows / .NET | `mimikatz`（内存加载抓密码）`petit_potam` `shellcode_load` `windows_privesc`（bad/sweet/efs/lemon 提权四件套）`sharp_web`（浏览器凭据）`csharp_memory_shell`（C# 内存马）|
| 哥斯拉库管理 | `plugin_list` `plugin_add` `plugin_remove` |

协议：MCP `2024-11-05`（JSON-RPC 2.0），端点 `/mcp`（HTTP 模式）或 stdio。

---

## 下载

- **方式一（推荐）**：前往 [Releases](https://github.com/Ch1ngg/Godzilla-MCP-Headless/releases) 下载 `godzilla-mcp-<版本>.jar` —— 由 GitHub Actions 在打 tag 时**自动构建发布**，无需本地编译。
- **方式二**：Fork 本仓库，推送到 `main` 后从 Actions 的 Artifacts 下载最新构建。
- **方式三**：自行构建（见「构建」）。

## 依赖

| 依赖 | 说明 |
|---|---|
| JDK 8+ | 运行与构建（JDK 8 / 11 / 17 实测可运行）|
| `godzilla.jar` | **运行时**需要，从你自己的哥斯拉安装目录获取（仅作类库）|
| 构建依赖 | 编译用 [Maven Central](https://central.sonatype.com/artifact/io.github.beichendream/godzilla) 的 `io.github.beichendream:godzilla:4.01`，**无需手工放置任何 jar** |

---

## 安装 & 使用

### 0. 获取

```bash
git clone https://github.com/Ch1ngg/Godzilla-MCP-Headless.git
cd Godzilla-MCP-Headless
# 从 Releases 下载 godzilla-mcp-<版本>.jar，放到仓库根目录（或任意位置并设置 MCP_JAR）
```

准备 `godzilla.jar`：放在任意目录，通过 `GZ_HOME` 指向（脚本自动探测 `./godzilla` → `~/Godzilla` → 当前目录）。

### 1. stdio 模式（推荐：Claude Desktop / Claude Code / Cursor）

客户端拉起进程、走标准输入输出：无需端口、无需常驻服务。

**Claude Desktop**（`claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "godzilla": {
      "command": "/绝对路径/Godzilla-MCP-Headless/scripts/run-stdio.sh",
      "env": { "GZ_HOME": "/你的哥斯拉目录（含 godzilla.jar 与 data.db）" }
    }
  }
}
```

**Claude Code**：

```bash
claude mcp add godzilla /绝对路径/Godzilla-MCP-Headless/scripts/run-stdio.sh
```

> `godzilla.jar` / `data.db` 就在 `~/Godzilla` 时，连 `env` 都不用配。

### 2. HTTP 模式（常驻 / 远程接入）

```bash
./scripts/run-http.sh                  # 默认 http://127.0.0.1:5566/mcp
./scripts/run-http.sh 8899 0.0.0.0     # 自定义端口 / 绑定地址
```

客户端（url 型）：

```json
{ "mcpServers": { "godzilla": { "url": "http://127.0.0.1:5566/mcp" } } }
```

> ⚠️ HTTP 模式无鉴权（沿袭上游设计），默认绑定 `127.0.0.1`；对外部署请自行加防火墙或反代鉴权。

### 3. 放回哥斯拉当插件（可选）

把 `godzilla-mcp-<版本>.jar` 放进哥斯拉 `shells/plugins/java/`，重启哥斯拉，顶部菜单启动即可。同一份 JAR 自动识别两种运行形态。

---

## 无图形界面（VPS）安装插件

哥斯拉把插件登记在 `data.db` 的 `plugin` 表（存的是 jar 的**绝对路径**文本）。无 GUI 环境三种方式任选：

**方式 A：MCP 工具**（服务已连通时，让 AI 直接管理）

```
plugin_list                       # 查看已注册插件
plugin_add     /opt/gz/plugins/MyPlugin.jar
plugin_remove  /opt/gz/plugins/MyPlugin.jar
```

**方式 B：自带脚本**（无需 JVM / 无需显示，纯 Python 标准库）

```bash
python3 scripts/plugin.py list
python3 scripts/plugin.py add    /opt/gz/plugins/MyPlugin.jar
python3 scripts/plugin.py remove /opt/gz/plugins/MyPlugin.jar
# --db 可指定 data.db 路径；默认探测 $GZ_HOME / $MCP_WORKDIR / ./ / ~/Godzilla
```

**方式 C：sqlite3 直接写库**（零依赖）

```bash
cd /opt/gz                                       # 与 data.db 同目录
sqlite3 data.db "INSERT OR IGNORE INTO plugin(pluginJarFile) VALUES ('/opt/gz/plugins/MyPlugin.jar');"
sqlite3 data.db "SELECT * FROM plugin;"
sqlite3 data.db "DELETE FROM plugin WHERE pluginJarFile='/opt/gz/plugins/MyPlugin.jar';"
```

> 注意：始终使用**绝对路径**（相对路径按哥斯拉进程工作目录解析）；目标 jar 不存在时哥斯拉会静默跳过（日志 `no found`）；修改后重启哥斯拉生效。
> 如果必须用 JVM 方式注册（`Db.addPlugin`），注意哥斯拉核心初始化会读取屏幕尺寸，无显示的 Linux 上先装 `xvfb`（`xvfb-run java ...`）；上述 A/B/C 三条路线均无此限制（A 也是通过核心库调用，但运行在已有虚拟显示的环境里；B/C 完全不需要）。

---

## 使用示例

对话式指令（AI 自动调用对应工具）：

```
连接 http://target/shell.jsp，密码 pass123，密钥 0123456789abcdef
在目标上执行 /bin/sh -c "id; uname -a"
把 /etc/passwd 下载到本地 /tmp/passwd
扫描 127.0.0.1 的 22,80,8080 端口
在目标注入内存马 /favicon.ico，密码 mem123，密钥 memkey123456
```

**生成哥斯拉马**：

```json
{ "name": "generate_shell", "arguments": {
    "cryption": "JAVA_AES_BASE64", "password": "pass123", "secretKey": "0123456789abcdef",
    "outputPath": "/tmp/shell.jsp" } }
```

生成后上传到目标，用同组「密码/密钥」+ 对应 payload/cryption 连接（JSP:`JavaDynamicPayload`+`JAVA_AES_BASE64`；PHP:`PhpDynamicPayload`+`PHP_XOR_BASE64`）。
密钥在生成时按哥斯拉规则自动派生 `md5(secretKey)[0:16]`。

支持的完整矩阵（与哥斯拉核心生成器逐字节一致）：
`JAVA_AES_BASE64/RAW`（suffix: jsp/jspx）、`PHP_XOR_BASE64/RAW`、`PHP_EVAL_XOR_BASE64`、`CSHAP_AES_BASE64/RAW`（suffix: aspx/asmx/ashx）、`CSHAP_ASMX_AES_BASE64`、`CSHAP_EVAL_AES_BASE64`、`ASP_XOR_BASE64/RAW`、`ASP_BASE64/RAW`、`ASP_EVAL_BASE64`。

### 注意事项

- **exec_command（Java 载荷）**：单进程 + 参数拆分语义，复合命令需包一层 shell：`/bin/sh -c "id; echo OK"`；PHP 载荷可直接 `;` 串联。
- **exec_code（PHP 载荷）**：依赖目标 PHP 的 `output_buffering > 0`（主流发行版默认 4096；PHP-CLI 内置服务器需 `-d output_buffering=4096`）。
- **内存马**：`memory_shell_inject` 支持 `AES_BASE64/AES_RAW/Behinder/Cknife/ReGeorg`，注入后可用同密码/密钥以 `JAVA_AES_BASE64` 连接；Servlet 型用 `memory_shell_list/unload` 管理，Filter 型用 `filter_shell_*` 管理。

---

## 环境变量

| 变量 | 作用 | 默认 |
|---|---|---|
| `GZ_HOME` | godzilla.jar 所在目录 | 自动探测 `./godzilla` → `~/Godzilla` → 当前目录 |
| `MCP_WORKDIR` | data.db 工作目录（决定使用哪个数据库）| 同 `GZ_HOME` |
| `MCP_JAR` | 插件 JAR 路径 | 自动查找 `dist/`、仓库根、`target/`、脚本同级 |
| `JAVA` | java 可执行文件 | `java` |
| `MCP_PORT` / `MCP_HOST` | HTTP 模式端口 / 绑定地址 | `5566` / `127.0.0.1` |

---

## 工作原理（为什么不用启动哥斯拉）

插件真正依赖 `godzilla.jar` 的只有三块能力：`core.Db`（读写 data.db）、`core.shell.ShellEntity` + `core.imp.Payload`（Webshell 通信协议）、HTTP 服务。无头启动器（`GodzillaMcpHeadlessBootstrap`）做三件事：

1. 设置 `godzilla.mcp.headless=true` → 跳过 GUI 菜单注册（放回哥斯拉运行时该标志不存在，菜单照常注册）；
2. 调用 `ApplicationContext.init()` 扫描 payload / cryption 注册表（哥斯拉 GUI 启动时会做，无头环境需手动补齐，否则会 NPE）；
3. 启动 MCP 服务（stdio 或 HTTP）。

注意：`data.db` 是 SQLite 相对路径（`jdbc:sqlite:data.db`），**工作目录决定用哪个数据库** —— 脚本会自动切换到 `MCP_WORKDIR`。

---

## 构建

### GitHub Actions（默认，推荐）

推送到 `main` / PR 自动构建；打 `v*` tag 自动创建 Release 并附带本 jar。工作流：`.github/workflows/build.yml`。

### 本地构建（可选）

```bash
mvn -q package          # 依赖来自 Maven Central，无需手工准备 lib/
# 产物: target/godzilla-mcp-<version>.jar
```

无 Maven 环境（可选，需本机有 godzilla.jar）：`./scripts/build.sh`（GZ_HOME 或 `lib/godzilla.jar`）。

---

## 自测（可选）

```bash
python3 test/smoke_test.py "http://127.0.0.1:18080/gz/shell.jsp" "pass123" "0123456789abcdef"
```

快速搭本地靶场（Docker + Tomcat）：

```bash
mkdir -p lab
# 用 generate_shell 生成 lab/shell.jsp（密码 pass123 / 密钥 0123456789abcdef）
docker run -d --name gz-lab -p 127.0.0.1:18080:8080 \
  -v "$PWD/lab:/usr/local/tomcat/webapps/gz" tomcat:9.0-jdk8-temurin
python3 test/smoke_test.py "http://127.0.0.1:18080/gz/shell.jsp" "pass123" "0123456789abcdef"
```

---

## 变更记录（摘要）

**v1.2.2**：修复 HTTP 模式未初始化核心注册表（连接类工具不可用）；启动日志如实显示绑定地址；macOS 默认隐藏 Dock 图标。
**v1.2.1**：`generate_shell` 全系生成（14 种 cryption，与核心生成器逐字节一致）；`get_env_config` 实时读取注册表。
**v1.2.0**：工具集 10 → 44（会话/文件/大文件/内存马/端口扫描/插件库管理等）；改用 Maven Central 编译依赖；CI 自动构建与发布；新增 `scripts/plugin.py`（VPS 插件管理）。
**v1.1.0**：无头启动器 + stdio 传输；`generate_shell`；修复 `ApplicationContext` 未初始化 NPE、`util.Log` 污染 stdio、密钥派生、`read_file` 接口。

完整记录见 [CHANGELOG.md](CHANGELOG.md)。

## 常见问题

**Q：一定要安装哥斯拉吗？** 不需要。只需要它的 `godzilla.jar` 与你的 `data.db` 两个文件。

**Q：stdio 模式的进程"退出"了？** stdio 服务器跟随客户端生命周期，属正常设计；需要常驻请用 HTTP 模式。

**Q：Linux 无显示环境跑不起来？** 装 `xvfb`（`apt install xvfb`）即可，脚本在无 `DISPLAY` 时自动改用 `xvfb-run`（原因：哥斯拉核心初始化会读取屏幕尺寸）。

**Q：macOS 下跑 stdio/HTTP 会闪出 java 的 Dock 图标？**  
A: 这是哥斯拉核心初始化读取屏幕尺寸触发的 AWT 注册（不是启动 GUI）。v1.2.2 起启动脚本在 macOS 默认附加 `-Dapple.awt.UIElement=true`，Dock 图标不再出现且功能不受影响；自定义 JVM 参数可用 `JAVA_OPTS` 环境变量。

**Q：会和哥斯拉 GUI 冲突吗？** 可共用同一个 `data.db`（读互不影响）；避免两边同时写入（如同时 `add_shell`）。

**Q：exec_code 报 "no load"？** 目标 PHP 需要 `output_buffering > 0`（发行版默认满足；CLI 内置服务器用 `-d output_buffering=4096` 启动）。

## 致谢

- [BeichenDream/Godzilla](https://github.com/BeichenDream/Godzilla) — 哥斯拉本体
- [cns1rius/godzilla-mcp](https://github.com/cns1rius/godzilla-mcp) — MCP 插件原始版本
- [hkdonline/Godzilla-MCP](https://github.com/hkdonline/Godzilla-MCP) — 本项目的直接上游

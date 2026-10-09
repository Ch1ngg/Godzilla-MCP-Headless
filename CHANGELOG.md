# Changelog

## v1.4.0 (2026-10-10)

### 新增（工具集 50 → 56，Windows / .NET 补全）
- **Windows / .NET 工具**：
  - `mimikatz`：内置 mimikatz PE 在客户端侧转 shellcode、目标内存中加载执行（Java / C# 载荷；参数默认 `privilege::debug` / `sekurlsa::logonpasswords` / `exit`）
  - `petit_potam`：内存加载 EfsPotato 执行命令（Java / C# 载荷）
  - `shellcode_load`：hex 或本地文件加载执行 shellcode（Java / C# 载荷；自动适配 AsmLoader / ShellcodeLoader + GodzillaJna）
  - `windows_privesc`：提权四件套 `bad`（BadPotato）/ `sweet`（SweetPotato，含 CLSID）/ `efs`（EfsPotato）/ `lemon`（Lemon）
  - `sharp_web`：内存加载 SharpWeb 读取浏览器密码/凭据
  - `csharp_memory_shell`：C# 内存马注入（addShell）+ bypassFriendlyUrlRoute / bypassPrecompiledApp
- **多语言路由扩展**：`zip`（CZip.Run）、`port_scan`（CProtScan.Run）、`real_cmd`（RealCmd.Run）新增 **C# 载荷分支**——三类工具现覆盖 Java / PHP / C# 全系

### 工程
- **Windows 启动脚本**：新增 `scripts/run-http.bat` 与 `scripts/run-stdio.bat`（功能与 .sh 对齐：MCP_JAR / GZ_HOME / MCP_WORKDIR 探测、端口参数、JAVA_OPTS、data.db 工作目录）

### 说明
- Windows 专属工具（mimikatz / potato 系 / SharpWeb 等）面向 Windows 目标，未在本仓库 Linux 实验室环境做实机回归；各工具调用序列与参数逐项对齐官方客户端实现

## v1.3.0 (2026-10-10)

### 新增（工具集 44 → 50，补齐语言专属能力）
- **通用**：
  - `real_cmd`：虚拟终端（交互式命令执行，PHP/Java 载荷）。`start/write/read/stop/list` 会话协议，等效哥斯拉「虚拟终端」插件；实测两端（PHP 8.5 与 Tomcat）完整 shell 语义（命令执行、变量展开、多命令连续）
- **PHP 专属**（5 个）：
  - `php_ps`：免命令进程列表（直接解析 `/proc`；`disable_functions` 禁用命令函数时仍可用）
  - `php_webshell_scan`：Webshell 特征扫描（正则匹配 PHP/INC，返回 file/line/code JSON）
  - `php_bypass_open_basedir`：绕过 open_basedir（写入会话标志，后续文件操作按上游资产逻辑解锁）
  - `php_bypass_disable_functions`：四种方式绕过 `disable_functions` 执行命令（`mem` 内存绕过 / `env` LD_PRELOAD / `fpm` 攻击 PHP-FPM / `amc` Apache Mod CGI）
  - `php_attack_fpm`：FastCGI 直打 PHP-FPM 执行任意代码（内置 FastCGI 客户端；已实测对 PHP 8.5-FPM 完整 RCE 并回显执行结果）
- **多语言路由**：`zip`、`port_scan`、`exec_code` 按载荷语言自动选择实现
  - `zip`：Java（JZip）/ PHP（PZip，ZipArchive）——两端实测通过
  - `port_scan`：Java（plugin.JPortScan）/ PHP（PortScan）——两端实测通过
  - `exec_code`：PHP（PHP_Eval_Code）/ ASP（AEvalCode）；Java / C# 上游无代码执行插件

### 修复
- **长会话偶发“失败后无法恢复”**：`onTarget` 重试路径在缓存失效且数据库中无该 URL 时会抛出 `Shell URL not found`，吞掉原始错误；现在 `connect_shell` / `add_shell` 记录会话凭据，缓存失效时**原位重建**（无需数据库），并保留原始错误信息
- `php_ps` 表头行被误当 Base64 解码产生乱码

### 兼容性说明
- `real_cmd` 需要目标支持**并发请求**：Apache / PHP-FPM / Nginx+FPM 直接可用；`php -S` 需 `PHP_CLI_SERVER_WORKERS>1`（单进程下长挂终端请求会占满内置服务器，上游插件机制如此）
- `php_bypass_open_basedir` / `php_bypass_disable_functions` / `php_attack_fpm` 使用上游资产（ant .so 为 x86_64；部分 mem payload 仅适用于 PHP 7.x / 特定扩展），实际生效范围以目标环境为准

## v1.2.2 (2026-10-10)

### 修复
- **HTTP 模式未初始化 `ApplicationContext`，导致连接类工具不可用**（`get_env_config` 为空、`connect_shell` / 目标操作类全部失败）；该初始化此前只挂在 stdio 分支，现已补齐
- 启动日志如实显示实际绑定地址（原先固定打印 `0.0.0.0`，易误判暴露面）

### 优化
- 启动脚本支持 `JAVA_OPTS`，并在 macOS 默认附加 `-Dapple.awt.UIElement=true`（消除 JVM 的 Dock 图标闪现；哥斯拉核心读屏幕尺寸的 AWT 初始化不再显示"java 图标"）

## v1.2.1 (2026-10-09)

### 增强
- `generate_shell` 扩展为**全系马生成**（14 种 cryption）：JSP（jsp/jspx）、PHP（XOR / EVAL）、C#（aspx/asmx/ashx）、ASP（5 系）
  - 与哥斯拉核心生成器**逐字节一致**：15/15 变体对比通过（含用影子 `GOptionPane` 驱动原版走完 jspx / C# 后缀弹窗路径后对比）
- `get_env_config` 改为实时读取核心注册表，列出全部 Payload 与加密方式（4 类 payload / 14 种 cryption）

## v1.2.0 (2026-10-09)

### 新增（工具集从 10 个扩展到 44 个）
- 会话管理：`connect_shell`（免保存直连）、`disconnect_shell`、`list_sessions`、`test_connection`、`remove_shell`
- 文件全家桶：`write_file` / `delete_file` / `copy_file` / `move_file` / `new_file` / `new_dir` / `list_root` / `file_size`
- 大文件与远程：`download_file`（自动分块）、`big_file_upload`（分块）、`file_remote_down`（目标直连下载）
- 系统信息：`current_user`、`process_list`、`network_info`、`screenshot`
- 高级能力：`port_scan`、内存马 `memory_shell_inject/list/unload`、Filter 马 `filter_shell_add/list/remove`、`zip`、`exec_code`（PHP）、`enum_database_conn`、`list_databases`
- 库管理（无 GUI 场景）：`plugin_list` / `plugin_add` / `plugin_remove`
  - 说明：`exec_code`（PHP）依赖目标 `output_buffering > 0`（发行版默认 4096；PHP-CLI 内置服务器需 `-d output_buffering=4096`）
- 工程：GitHub Actions 自动构建与发布（打 `v*` tag 自动出 Release）；`scripts/plugin.py`（无 JVM 直接管理插件库）

### 变更
- 构建依赖改用 Maven Central 的 `io.github.beichendream:godzilla:4.01`（编译不再需要手工放置 `lib/godzilla.jar`）
- 移除仓库内 `dist/` 构建产物（产物由 CI/Release 提供）

## v1.1.0 Headless Edition (2026-10-09)

### 新增
- 无头模式：`GodzillaMcpHeadlessBootstrap` 启动器 + `--stdio` 传输（Claude Desktop / Claude Code / Cursor 等直接接入，无需启动哥斯拉 GUI）
- `generate_shell` 工具：生成 JSP / PHP 哥斯拉马（`JAVA_AES_BASE64` / `JAVA_AES_RAW` / `PHP_XOR_BASE64` / `PHP_XOR_RAW`）
- HTTP 模式支持自定义端口与绑定地址（默认 `127.0.0.1:5566`）

### 修复
- 无头环境缺少 `ApplicationContext.init()` 导致 `ShellEntity` NPE（cryption / payload 注册表为空）
- `util.Log` 输出到 stdout，污染 stdio 的 JSON-RPC 流（已隔离至 stderr）
- `generate_shell` 密钥派生错误：与 `JavaAesBase64.generate` / `PhpXor.generate` 对齐为 `md5(secretKey)[0:16]`
- `read_file` 使用 `payload.getFile()`（目录枚举）而非 `downloadFile()`（读取内容）

### 工程
- 启动脚本完全可移植：基于脚本自身目录定位 + `GZ_HOME` / `MCP_JAR` / `JAVA` / `MCP_WORKDIR` 环境变量
- 新增无 Maven 构建脚本 `scripts/build.sh`
- 新增冒烟测试 `test/smoke_test.py`

## v1.1.0 Headless Edition (2026-10-09)

### 新增
- 无头模式：`GodzillaMcpHeadlessBootstrap` 启动器 + `--stdio` 传输（Claude Desktop / Claude Code / Cursor 等直接接入，无需启动哥斯拉 GUI）
- `generate_shell` 工具：生成 JSP / PHP 哥斯拉马（`JAVA_AES_BASE64` / `JAVA_AES_RAW` / `PHP_XOR_BASE64` / `PHP_XOR_RAW`）
- HTTP 模式支持自定义端口与绑定地址（默认 `127.0.0.1:5566`）

### 修复
- 无头环境缺少 `ApplicationContext.init()` 导致 `ShellEntity` NPE（cryption / payload 注册表为空）
- `util.Log` 输出到 stdout，污染 stdio 的 JSON-RPC 流（已隔离至 stderr）
- `generate_shell` 密钥派生错误：与 `JavaAesBase64.generate` / `PhpXor.generate` 对齐为 `md5(secretKey)[0:16]`
- `read_file` 使用 `payload.getFile()`（目录枚举）而非 `downloadFile()`（读取内容）

### 工程
- 启动脚本完全可移植：基于脚本自身目录定位 + `GZ_HOME` / `MCP_JAR` / `JAVA` / `MCP_WORKDIR` 环境变量
- 新增无 Maven 构建脚本 `scripts/build.sh`
- 新增冒烟测试 `test/smoke_test.py`

## 上游 v1.0.12（继承自 hkdonline/Godzilla-MCP）
- Payload 缓存自动重试（会话过期自动恢复）
- 移除 AUTH_TOKEN（无需接口认证）
- exec_command 输出统一 Base64 包装（防非 UTF-8 炸序列化）
- 全量 NPE 防护与堆栈日志

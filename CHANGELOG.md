# Changelog

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

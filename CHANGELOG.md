# Changelog

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

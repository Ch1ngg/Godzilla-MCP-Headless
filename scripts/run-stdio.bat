@echo off
setlocal
rem ============================================================
rem Godzilla-MCP Headless - stdio mode launcher (Windows)
rem Use as MCP client "command" (Claude Desktop / Claude Code /
rem Cursor) or run in a terminal directly.
rem
rem Optional environment variables:
rem   GZ_HOME      folder containing godzilla.jar
rem                (auto-detect: .\godzilla -> %USERPROFILE%\Godzilla -> CWD)
rem   MCP_WORKDIR  data.db working directory (default: same as GZ_HOME)
rem   MCP_JAR      plugin jar path (default: auto-find dist/ or target/)
rem   JAVA         java executable (default: java)
rem   JAVA_OPTS    extra JVM options
rem ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_DIR=%%~fI"

rem ---- locate plugin jar ----
if not defined MCP_JAR for %%F in ("%REPO_DIR%\dist\godzilla-mcp-*.jar" "%REPO_DIR%\godzilla-mcp-*.jar" "%REPO_DIR%\target\godzilla-mcp-*.jar" "%SCRIPT_DIR%godzilla-mcp-*.jar") do if not defined MCP_JAR if exist "%%~fF" set "MCP_JAR=%%~fF"
if not defined MCP_JAR (
    echo [!] Plugin JAR not found. Build it first ^(see README^) or set MCP_JAR=^<path^>\godzilla-mcp-*.jar 1>&2
    exit /b 1
)

rem ---- locate Godzilla home (godzilla.jar) ----
if not defined GZ_HOME if exist "%REPO_DIR%\godzilla\godzilla.jar" set "GZ_HOME=%REPO_DIR%\godzilla"
if not defined GZ_HOME if exist "%USERPROFILE%\Godzilla\godzilla.jar" set "GZ_HOME=%USERPROFILE%\Godzilla"
if not defined GZ_HOME if exist "%CD%\godzilla.jar" set "GZ_HOME=%CD%"
if not defined GZ_HOME (
    echo [!] godzilla.jar not found. Set GZ_HOME to your Godzilla install folder, e.g. 1>&2
    echo     set GZ_HOME=C:\path\to\Godzilla 1>&2
    exit /b 1
)

rem ---- data.db working directory ----
if defined MCP_WORKDIR (cd /d "%MCP_WORKDIR%") else (cd /d "%GZ_HOME%")

if not defined JAVA set "JAVA=java"

set "CP=%MCP_JAR%;%GZ_HOME%\godzilla.jar"

echo [Godzilla-MCP] stdio mode ^| jar=%MCP_JAR% ^| db=%CD%\data.db 1>&2

"%JAVA%" %JAVA_OPTS% -cp "%CP%" shells.plugins.online.GodzillaMcpHeadlessBootstrap --stdio

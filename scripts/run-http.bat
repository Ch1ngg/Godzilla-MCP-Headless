@echo off
setlocal
rem ============================================================
rem Godzilla-MCP Headless - HTTP/SSE mode launcher (Windows)
rem Usage: run-http.bat [port] [bind-host]
rem   port        default 5566
rem   bind-host   default 127.0.0.1 (do not expose without auth)
rem
rem Optional environment variables:
rem   GZ_HOME / MCP_WORKDIR / MCP_JAR / JAVA / JAVA_OPTS
rem   MCP_PORT / MCP_HOST  (positional args take precedence)
rem ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_DIR=%%~fI"

set "PORT=%~1"
if not defined PORT if defined MCP_PORT set "PORT=%MCP_PORT%"
if not defined PORT set "PORT=5566"
set "HOST=%~2"
if not defined HOST if defined MCP_HOST set "HOST=%MCP_HOST%"
if not defined HOST set "HOST=127.0.0.1"

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
    echo [!] godzilla.jar not found. Set GZ_HOME to your Godzilla install folder. 1>&2
    exit /b 1
)
if not exist "%GZ_HOME%\godzilla.jar" (
    echo [!] godzilla.jar missing under GZ_HOME: %GZ_HOME% 1>&2
    exit /b 1
)

rem ---- data.db working directory ----
if defined MCP_WORKDIR (cd /d "%MCP_WORKDIR%") else (cd /d "%GZ_HOME%")

if not defined JAVA set "JAVA=java"

set "CP=%MCP_JAR%;%GZ_HOME%\godzilla.jar"

echo [Godzilla-MCP] HTTP mode: http://%HOST%:%PORT%/mcp  ^(Ctrl+C to exit^) 1>&2

"%JAVA%" %JAVA_OPTS% -cp "%CP%" shells.plugins.online.GodzillaMcpHeadlessBootstrap %PORT% %HOST%

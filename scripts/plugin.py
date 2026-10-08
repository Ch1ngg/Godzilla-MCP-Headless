#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Godzilla 插件注册管理（无需 JVM / 无需图形界面，直接操作 data.db）

用途: 在 VPS 等无图形界面环境下，把插件 jar 注册进哥斯拉
      （等价于哥斯拉 GUI 的「插件管理 -> 添加」，直接写 data.db 的 plugin 表）。

用法:
  python3 scripts/plugin.py list
  python3 scripts/plugin.py add    /absolute/path/MyPlugin.jar
  python3 scripts/plugin.py remove /absolute/path/MyPlugin.jar

选项:
  --db PATH   指定 data.db 路径
              （默认自动探测: $GZ_HOME -> $MCP_WORKDIR -> 当前目录 -> ~/Godzilla）

说明:
  - 请始终使用绝对路径注册（哥斯拉按进程工作目录解析相对路径）；
  - 目标 jar 不存在时，哥斯拉启动时会静默跳过（日志显示 no found）；
  - 修改后重启哥斯拉生效。
"""
import os
import sqlite3
import sys


def parse_args():
    db = None
    rest = []
    argv = sys.argv[1:]
    i = 0
    while i < len(argv):
        if argv[i] == "--db" and i + 1 < len(argv):
            db = argv[i + 1]
            i += 2
        else:
            rest.append(argv[i])
            i += 1
    return db, rest


def find_db(explicit):
    if explicit:
        return explicit
    cands = []
    for env in ("GZ_HOME", "MCP_WORKDIR"):
        root = os.environ.get(env)
        if root:
            cands.append(os.path.join(root, "data.db"))
    cands.append(os.path.join(os.getcwd(), "data.db"))
    cands.append(os.path.expanduser("~/Godzilla/data.db"))
    for c in cands:
        if os.path.isfile(c):
            return c
    return None


def main():
    db, args = parse_args()
    if not args:
        print(__doc__)
        sys.exit(1)
    db = find_db(db)
    if not db:
        print("[!] 未找到 data.db，请用 --db /path/to/data.db 指定")
        sys.exit(1)
    print(f"[*] data.db: {db}")
    conn = sqlite3.connect(db)
    cmd = args[0]
    if cmd == "list":
        rows = list(conn.execute("SELECT pluginJarFile FROM plugin ORDER BY pluginJarFile"))
        if not rows:
            print("(空)")
        for (p,) in rows:
            print(p)
    elif cmd == "add" and len(args) > 1:
        path = os.path.abspath(args[1])
        if not os.path.isfile(path):
            print(f"[!] 文件不存在: {path}")
            sys.exit(1)
        try:
            conn.execute("INSERT INTO plugin(pluginJarFile) VALUES (?)", (path,))
            conn.commit()
            print(f"✓ 已注册: {path}（重启哥斯拉后生效）")
        except sqlite3.IntegrityError:
            print(f"已存在: {path}")
    elif cmd == "remove" and len(args) > 1:
        cur = conn.execute("DELETE FROM plugin WHERE pluginJarFile=?", (args[1],))
        conn.commit()
        print(("✓ 已移除: " if cur.rowcount else "未找到: ") + args[1])
    else:
        print(__doc__)
    conn.close()


if __name__ == "__main__":
    main()

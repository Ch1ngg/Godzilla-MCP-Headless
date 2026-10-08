# lib/ 目录说明

构建需要以下两个 jar（均已加入 .gitignore，不会提交）：

1. **godzilla.jar** —— 从你自己的哥斯拉安装目录复制过来（仅编译期需要；运行时会从 `GZ_HOME` 指向的目录读取）。

   ```bash
   cp /path/to/Godzilla/godzilla.jar lib/
   ```

2. **gson-2.10.1.jar** —— 使用 `scripts/build.sh` 时会自动下载；使用 Maven 构建时无需放置。

构建：

```bash
./scripts/build.sh        # 或 mvn -q package
```

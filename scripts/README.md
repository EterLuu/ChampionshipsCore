# 维护脚本

从仓库根目录运行以下脚本。这里仅保存持续使用的开发检查和只读审查；一次性迁移、地图制作、模拟程序及报告放在 `/tmp`。

| 脚本 | 用途 | 依赖 |
| --- | --- | --- |
| `format-java.sh` | 按 Google Java Format 1.33.0 AOSP 样式格式化或检查 Maven Java 源码 | Bash、Git、ripgrep、curl、JDK 25、Python 3.11+ |
| `check-source-layout.py` | 检查生产/测试分离、package 路径和领域归属 | Python 3.11+ 标准库 |
| `buildmart_blueprint_audit.py` | 只读检查蓝图难度与材料区覆盖，输出 Markdown、CSV、JSON | Python 3.11+ 标准库 |

## 开发检查

```bash
scripts/format-java.sh --check
python3 scripts/check-source-layout.py
```

省略 `--check` 会格式化 Java 文件。格式器首次运行下载固定版本 JAR 到 `/tmp`，不在仓库保存工具二进制。

Python 格式由根目录 `pyproject.toml` 管理。可在临时虚拟环境安装固定版本 Ruff：

```bash
python3 -m venv /tmp/cc-script-format-venv
/tmp/cc-script-format-venv/bin/pip install ruff==0.11.13
/tmp/cc-script-format-venv/bin/ruff format --check scripts
```

省略 `--check` 会格式化 Python 脚本；运行脚本本身不需要 Ruff 或 PyYAML。

## BuildMart 审查

先在插件内保存材料区，取得对应 `material-manifests/<地图>.yml`，再执行：

```bash
python3 scripts/buildmart_blueprint_audit.py <蓝图文件或目录> \
  --manifest <材料清单.yml> --area-config <地图.yml> \
  --markdown /tmp/buildmart-audit.md --csv /tmp/buildmart-audit.csv \
  --json /tmp/buildmart-audit.json
```

参数中的文件名需替换为实际路径。脚本不改蓝图、配置或世界；支持的加工来源不能替代当前服务端配方和生存摆放验证。判定与原生检查见 [蓝图验证指南](../docs/buildmart-blueprint-validation.md)。

#!/usr/bin/env python3
"""Check Maven source ownership and package layout without generated build outputs."""

from pathlib import Path
import re
import sys


def check(root: Path) -> tuple[int, list[str]]:
    errors = []
    count = 0
    core = root / "championships-core/src/main/java/ink/ziip/championshipscore"
    infrastructure = {
        "area",
        "arena",
        "config",
        "instance",
        "manager",
        "setup",
        "spatial",
        "start",
        "spectate",
        "model",
    }
    for module in sorted(root.glob("championships-*")):
        for kind in ("main", "test"):
            source = module / "src" / kind / "java"
            for file in sorted(source.rglob("*.java")):
                count += 1
                text = file.read_text(encoding="utf-8")
                relative = file.relative_to(root)
                match = re.search(r"^package ([\w.]+);", text, re.MULTILINE)
                if (
                    match is None
                    or file.relative_to(source) != Path(*match[1].split(".")) / file.name
                ):
                    errors.append(f"{relative}: package must match its source directory")
                if kind == "main" and re.search(
                    r"import (?:org\.junit|org\.mockito)|@(?:Test|ParameterizedTest)\b", text
                ):
                    errors.append(f"{relative}: test code belongs in src/test/java")
                if kind != "main" or not file.is_relative_to(core):
                    continue
                owned = file.relative_to(core)
                if "database" not in owned.parts and re.search(
                    r"\b(?:java\.sql\.(?!SQLException\b)[\w*]+|javax\.sql\.|com\.zaxxer\.)", text
                ):
                    errors.append(f"{relative}: JDBC implementation belongs in database")
                if re.search(
                    r"\bLocation\.deserialize\s*\(|import ink\.ziip\.championshipscore\.util\.Utils;",
                    text,
                ):
                    errors.append(
                        f"{relative}: use the shared configuration/presentation entry points"
                    )
                if (
                    file.name.endswith("Manager.java")
                    and owned.parts[:2] == ("api", "game")
                    and file.name != "BaseGameInstanceManager.java"
                    and re.search(r"instancesByMap\s*=\s*new", text)
                ):
                    errors.append(
                        f"{relative}: runtime-copy registry belongs in BaseGameInstanceManager"
                    )
                if owned.parts[:2] == ("api", "object"):
                    errors.append(
                        f"{relative}: models belong in their owning game or schedule domain"
                    )
                if owned.parts[:2] == ("api", "game") and len(owned.parts) == 4:
                    game = owned.parts[2]
                    if game not in infrastructure and not file.stem.endswith("Manager"):
                        errors.append(
                            f"{relative}: game root contains only its registration Manager"
                        )
    for file in sorted(root.glob("championships-*/src/main/resources/**/*.yml")):
        if re.search(
            r"^\s*==:\s*['\"]?org\.bukkit\.Location", file.read_text(encoding="utf-8"), re.MULTILINE
        ):
            errors.append(f"{file.relative_to(root)}: Location must be a raw coordinate section")
    return count, errors


def main() -> int:
    count, errors = check(Path(__file__).resolve().parents[1])
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f"Source ownership and package layout passed: {count} Java files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

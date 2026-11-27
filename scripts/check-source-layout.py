#!/usr/bin/env python3
"""Check Maven source ownership and package layout without generated build outputs."""

import os
from pathlib import Path
import re
import sys


TEST_ANNOTATION = re.compile(
    r"^\s*@(?:org\.junit\.jupiter\.(?:api|params)\.)?"
    r"(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b",
    re.MULTILINE,
)


def java_files(module: Path):
    for directory, children, files in os.walk(module):
        if Path(directory) == module and "target" in children:
            children.remove("target")
        children.sort()
        for name in sorted(files):
            if name.endswith(".java"):
                yield Path(directory) / name


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
    for pom in sorted(root.glob("*/pom.xml")):
        module = pom.parent
        for file in java_files(module):
            count += 1
            relative = file.relative_to(module)
            if relative.parts[:3] not in (("src", "main", "java"), ("src", "test", "java")):
                errors.append(
                    f"{file.relative_to(root)}: Java belongs in src/main/java or src/test/java"
                )
                continue
            kind = relative.parts[1]
            source = module / "src" / kind / "java"
            text = file.read_text(encoding="utf-8")
            relative = file.relative_to(root)
            match = re.search(r"^package ([\w.]+);", text, re.MULTILINE)
            if match is None or file.relative_to(source) != Path(*match[1].split(".")) / file.name:
                errors.append(f"{relative}: package must match its source directory")
            if kind == "main" and (
                re.search(r"import (?:static )?(?:org\.junit|org\.mockito)\b", text)
                or TEST_ANNOTATION.search(text)
            ):
                errors.append(f"{relative}: test code belongs in src/test/java")
            if (
                kind == "test"
                and TEST_ANNOTATION.search(text)
                and not re.fullmatch(r"(?:Test.+|.+Tests?|.+TestCase)\.java", file.name)
            ):
                errors.append(f"{relative}: test name must match Surefire discovery (*Test.java)")
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
                errors.append(f"{relative}: use the shared configuration/presentation entry points")
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
                errors.append(f"{relative}: models belong in their owning game or schedule domain")
            if owned.parts[:2] == ("api", "game") and len(owned.parts) == 4:
                game = owned.parts[2]
                if game not in infrastructure and not file.stem.endswith("Manager"):
                    errors.append(f"{relative}: game root contains only its registration Manager")
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

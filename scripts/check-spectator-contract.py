#!/usr/bin/env python3
"""Report direct spectator-mode changes outside the unified spectator manager.

The default mode is advisory so the existing codebase can adopt the check without
breaking builds. Use --strict after migrating all callers.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


SPECTATOR_CALL = re.compile(r"\bsetGameMode\s*\(\s*GameMode\.SPECTATOR\b")
ALLOWED_NAMES = {"SpectatorManager.java", "SpectatorStateService.java"}


def find_violations(root: Path) -> list[tuple[Path, int, str]]:
    source_roots = [
        root / "championships-core/src/main/java",
        root / "championships-platform-bukkit/src/main/java",
    ]
    violations: list[tuple[Path, int, str]] = []
    for source_root in source_roots:
        if not source_root.exists():
            continue
        for path in sorted(source_root.rglob("*.java")):
            if path.name in ALLOWED_NAMES:
                continue
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if SPECTATOR_CALL.search(line):
                    violations.append((path.relative_to(root), number, line.strip()))
    return violations


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    violations = find_violations(root)
    print(f"Direct spectator-mode calls: {len(violations)}")
    for path, number, line in violations:
        print(f"{path}:{number}: {line}")
    if violations and args.strict:
        return 1
    if violations:
        print("Advisory mode: migrate callers before enabling --strict.")
    else:
        print("Spectator contract check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
